package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interface graphique pour le système de métiers (27 slots)
 * Layout: Métier actif au centre, rappel avantages, talents, kits, quêtes
 */
public class ProfessionGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey professionKey;
    private final NamespacedKey talentKey;
    private final NamespacedKey targetLevelKey;

    // Slots du menu (27 slots)
    private static final int ACTIVE_PROFESSION_SLOT = 13; // Centre
    private static final int TALENTS_BUTTON_SLOT = 11; // Bouton talents
    private static final int KIT_UPGRADE_SLOT = 12; // Amélioration kit
    private static final int CHANGE_PROFESSION_SLOT = 14; // Changer métier
    private static final int REWARDS_SLOT = 15; // Récompenses
    private static final int KITS_SLOT = 20; // Future feature
    private static final int QUESTS_SLOT = 24; // Future feature
    private static final int CLOSE_SLOT = 26; // Fermer
    private static final int HELP_SLOT = 18; // Aide

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
        Inventory gui = Bukkit.createInventory(null, 27, "§e⚒ §lMétiers §e⚒");

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
            gui.setItem(TALENTS_BUTTON_SLOT, createTalentsButton(activeProfession));
            gui.setItem(KIT_UPGRADE_SLOT, createKitUpgradeItem(player, activeProfession));
            gui.setItem(CHANGE_PROFESSION_SLOT, createChangeProfessionButton());
            gui.setItem(REWARDS_SLOT, createRewardsButton(player, activeProfession));
        } else {
            gui.setItem(ACTIVE_PROFESSION_SLOT, createChooseProfessionItem());
        }

        // Boutons informatifs et navigation
        gui.setItem(KITS_SLOT, createFutureFeatureItem("Kits", Material.CHEST, "§6Kits de métier", "§7À venir plus tard"));
        gui.setItem(QUESTS_SLOT, createFutureFeatureItem("Quêtes", Material.BOOK, "§eQuêtes métier", "§7À venir plus tard"));
        gui.setItem(HELP_SLOT, createHelpItem());
        gui.setItem(CLOSE_SLOT, createCloseItem());
    }

    /**
     * Crée l'item d'affichage du métier actif (non cliquable pour les talents)
     */
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

        meta.setDisplayName(profession.getDisplayName() + " §7(Actif)");

        List<String> lore = new ArrayList<>();
        lore.add("§7" + profession.getDescription());
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

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton d'accès aux talents
     */
    private ItemStack createTalentsButton(String professionId) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§5⭐ §lTalents");

        List<String> lore = new ArrayList<>();
        lore.add("§7Gérez vos talents de métier");
        lore.add("");
        lore.add("§7Améliorez vos capacités en");
        lore.add("§7dépensant de l'expérience joueur");
        lore.add("");
        lore.add("§e▶ Cliquez pour ouvrir !");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "view_talents");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item d'amélioration du kit
     */
    private ItemStack createKitUpgradeItem(Player player, String professionId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int kitLevel = playerData.getKitLevel(professionId); // CORRIGÉ: utilise getKitLevel

        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6📦 §lAmélioration Kit §7(Niv. " + kitLevel + "/10)");

        List<String> lore = new ArrayList<>();
        lore.add("§7Améliorez votre kit de métier");
        lore.add("");
        lore.add("§7Niveau actuel: §e" + kitLevel + "§7/§e10");

        if (kitLevel < 10) {
            int nextLevel = kitLevel + 1;
            int professionLevel = playerData.getProfessionLevel(professionId);

            if (professionLevel >= nextLevel) {
                long cost = (long) (2000 * Math.pow(1.8, nextLevel - 1)); // Coût différent des talents
                lore.add("§7Coût niveau " + nextLevel + ": §e" + NumberFormatter.format(cost) + " XP");
                lore.add("§e▶ Cliquez pour améliorer !");
            } else {
                lore.add("§cNiveau de métier requis: " + nextLevel);
            }
        } else {
            lore.add("§a✅ Kit maximal !");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_kit");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de changement de métier
     */
    private ItemStack createChangeProfessionButton() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c🔄 §lChanger de Métier");

        List<String> lore = new ArrayList<>();
        lore.add("§7Changez votre métier actif");
        lore.add("");
        lore.add("§c💸 Coût: §e5000 beacons");
        lore.add("§c⏰ Cooldown: §e24 heures");
        lore.add("");
        lore.add("§e▶ Cliquez pour changer !");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "change_profession");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton des récompenses
     */
    private ItemStack createRewardsButton(Player player, String professionId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int level = playerData.getProfessionLevel(professionId);

        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e🎁 §lRécompenses");

        List<String> lore = new ArrayList<>();
        lore.add("§7Récompenses de progression");
        lore.add("");
        lore.add("§7Niveau actuel: §e" + level + "§7/§e10");
        lore.add("");
        lore.add("§7Récompenses par niveau:");
        lore.add("§7• Niv. 1-3: §eBeacons et cristaux");
        lore.add("§7• Niv. 4-6: §6Clés et équipements");
        lore.add("§7• Niv. 7-10: §dLivres et déblocages");
        lore.add("");
        lore.add("§c§lÀ venir plus tard");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item pour choisir un métier
     */
    private ItemStack createChooseProfessionItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e⚒ §lChoisir un Métier");

        List<String> lore = new ArrayList<>();
        lore.add("§7Vous n'avez pas encore choisi de métier !");
        lore.add("");
        lore.add("§7Métiers disponibles:");
        lore.add("§7• §a§lMineur §7- Maître de l'extraction");
        lore.add("§7• §6§lCommerçant §7- Maître de l'économie");
        lore.add("§7• §c§lGuerrier §7- Maître du combat");
        lore.add("");
        lore.add("§e▶ Cliquez pour choisir !");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_profession");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item de talent
     */
    private ItemStack createTalentItem(Player player, String professionId, ProfessionManager.ProfessionTalent talent) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(professionId);
        int talentLevel = playerData.getTalentLevel(professionId, talent.getId());

        ItemStack item = new ItemStack(getTalentMaterial(talent.getId()));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6" + talent.getDisplayName() + " §7(Niv. " + talentLevel + "/10)");

        List<String> lore = new ArrayList<>();
        lore.add("§7" + talent.getDescription());
        lore.add("");

        // Affiche les valeurs pour les 10 niveaux
        for (int i = 1; i <= 10; i++) {
            String prefix = (i <= talentLevel) ? "§a✓ " : (i <= professionLevel) ? "§e⭘ " : "§c✗ ";
            int value = talent.getValueAtLevel(i);
            String suffix = talent.getId().contains("multiplier") ? "x" : "%";
            lore.add(prefix + "Niveau " + i + ": §e+" + value + suffix);
        }

        lore.add("");

        if (talentLevel < 10) {
            int nextLevel = talentLevel + 1;
            if (professionLevel >= nextLevel) {
                long cost = (long) (1000 * Math.pow(2, nextLevel - 1));
                lore.add("§7Coût niveau " + nextLevel + ": §e" + NumberFormatter.format(cost) + " XP");
                lore.add("§e▶ Cliquez pour améliorer !");
            } else {
                lore.add("§cNiveau de métier requis: " + nextLevel);
            }
        } else {
            lore.add("§a✅ Talent maximal !");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_talent");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        meta.getPersistentDataContainer().set(talentKey, PersistentDataType.STRING, talent.getId());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item d'information sur les talents
     */
    private ItemStack createTalentsInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§5📖 §lTalents de Métier");

        List<String> lore = new ArrayList<>();
        lore.add("§7Les talents se débloquent lorsque le");
        lore.add("§7niveau de métier requis est atteint.");
        lore.add("");
        lore.add("§7Pour les activer, vous devez dépenser");
        lore.add("§7de l'expérience joueur (XP) avec un");
        lore.add("§7coût exponentiel par talent.");
        lore.add("");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        lore.add("§7Votre XP: §e" + NumberFormatter.format(playerData.getExperience()));

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item pour les fonctionnalités futures
     */
    private ItemStack createFutureFeatureItem(String name, Material material, String description, String status) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7" + name);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + description);
        lore.add("");
        lore.add("§c" + status);

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item d'aide
     */
    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e❓ §lAide");

        List<String> lore = new ArrayList<>();
        lore.add("§7Commandes utiles:");
        lore.add("§e/metier info §7- Infos sur votre métier");
        lore.add("§e/changemetier <métier> §7- Changer de métier");
        lore.add("");
        lore.add("§7Débloquage: §eRang F §7requis");
        lore.add("§7Changement: §e5000 beacons §7+ §e24h cooldown");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item de fermeture
     */
    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c✗ §lFermer");
        meta.setLore(Arrays.asList("§7Ferme ce menu"));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Ouvre le menu de changement de métier
     */
    public void openChangeProfessionMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentProfession = playerData.getActiveProfession();

        Inventory gui = Bukkit.createInventory(null, 27, "§c🔄 §lChanger de Métier §c🔄");

        fillWithGlass(gui);

        // Informations sur le changement
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e💡 §lInformations");

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Métier actuel: §e" + (currentProfession != null ?
                plugin.getProfessionManager().getProfession(currentProfession).getDisplayName() : "Aucun"));
        infoLore.add("");
        infoLore.add("§c💸 Coût: §e5000 beacons");
        infoLore.add("§c⏰ Cooldown: §e24 heures");
        infoLore.add("");
        infoLore.add("§a✅ Votre progression est conservée !");

        // Vérification du cooldown
        long lastChange = playerData.getLastProfessionChange();
        long cooldownTime = 24 * 60 * 60 * 1000; // 24h en ms
        long timeLeft = (lastChange + cooldownTime) - System.currentTimeMillis();

        if (timeLeft > 0) {
            long hoursLeft = timeLeft / (60 * 60 * 1000);
            long minutesLeft = (timeLeft % (60 * 60 * 1000)) / (60 * 1000);
            infoLore.add("§c⏰ Cooldown actif: " + hoursLeft + "h " + minutesLeft + "m");
        } else {
            infoLore.add("§a✅ Prêt à changer !");
        }

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // Les 3 métiers (sauf celui actuel)
        String[] professions = {"mineur", "commercant", "guerrier"};
        int[] slots = {11, 13, 15};

        for (int i = 0; i < professions.length; i++) {
            if (!professions[i].equals(currentProfession)) {
                gui.setItem(slots[i], createProfessionChangeItem(professions[i]));
            } else {
                // Métier actuel (non cliquable)
                ItemStack current = createProfessionChoiceItem(professions[i]);
                ItemMeta currentMeta = current.getItemMeta();
                currentMeta.setDisplayName("§a" + currentMeta.getDisplayName() + " §7(Actuel)");
                List<String> lore = new ArrayList<>(currentMeta.getLore());
                lore.clear();
                lore.add("§7Votre métier actuel");
                lore.add("§c❌ Déjà sélectionné");
                currentMeta.setLore(lore);
                currentMeta.getPersistentDataContainer().remove(actionKey);
                current.setItemMeta(currentMeta);
                gui.setItem(slots[i], current);
            }
        }

        // Bouton retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§7← §lRetour");
        backMeta.setLore(Arrays.asList("§7Retour au menu métiers"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
        back.setItemMeta(backMeta);
        gui.setItem(22, back);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Crée un item pour changer vers un métier spécifique
     */
    private ItemStack createProfessionChangeItem(String professionId) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return new ItemStack(Material.BARRIER);

        Material material = getProfessionMaterial(professionId);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(profession.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + profession.getDescription());
        lore.add("");
        lore.add("§7Talents:");
        for (ProfessionManager.ProfessionTalent talent : profession.getTalents()) {
            lore.add("§7• §e" + talent.getDisplayName());
        }
        lore.add("");
        lore.add("§c💸 Coût: §e5000 beacons");
        lore.add("§a▶ Cliquez pour changer !");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm_change");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item pour choisir un métier spécifique
     */
    private ItemStack createProfessionChoiceItem(String professionId) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return new ItemStack(Material.BARRIER);

        Material material = getProfessionMaterial(professionId);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(profession.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + profession.getDescription());
        lore.add("");
        lore.add("§7Talents:");
        for (ProfessionManager.ProfessionTalent talent : profession.getTalents()) {
            lore.add("§7• §e" + talent.getDisplayName());
        }
        lore.add("");
        lore.add("§a▶ Cliquez pour choisir ce métier !");
        lore.add("§7(Premier choix gratuit)");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select_profession");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Ouvre le menu détaillé des talents (2 pages: 5+5)
     */
    public void openTalentMenu(Player player, String professionId, int page) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return;

        Inventory gui = Bukkit.createInventory(null, 54, "§5⭐ Talents " + profession.getDisplayName() + " §7(Page " + (page + 1) + "/2)");

        fillWithGlass(gui);
        setupTalentMenuPage(gui, player, profession, page);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Configure une page du menu des talents
     */
    private void setupTalentMenuPage(Inventory gui, Player player, ProfessionManager.Profession profession, int page) {
        List<ProfessionManager.ProfessionTalent> talents = profession.getTalents();
        int startIndex = page * 5;
        int endIndex = Math.min(startIndex + 5, talents.size());

        // Affiche les talents de cette page
        int[] slots = {20, 21, 22, 23, 24}; // Centre de l'inventaire
        for (int i = startIndex; i < endIndex; i++) {
            ProfessionManager.ProfessionTalent talent = talents.get(i);
            int slotIndex = i - startIndex;
            gui.setItem(slots[slotIndex], createDetailedTalentItem(player, profession.getId(), talent));
        }

        // Navigation
        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName("§7← §lPage Précédente");
            prevMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "prev_page");
            prevMeta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, profession.getId());
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        if (endIndex < talents.size()) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName("§7→ §lPage Suivante");
            nextMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next_page");
            nextMeta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, profession.getId());
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        // Retour
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c← §lRetour");
        backMeta.setLore(Arrays.asList("§7Retour au menu métiers"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
        back.setItemMeta(backMeta);
        gui.setItem(49, back);
    }

    /**
     * Crée un item de talent détaillé pour le menu des talents
     */
    private ItemStack createDetailedTalentItem(Player player, String professionId, ProfessionManager.ProfessionTalent talent) {
        // Similaire à createTalentItem mais avec plus de détails
        return createTalentItem(player, professionId, talent);
    }

    /**
     * Ouvre le menu de sélection de métier (premier choix)
     */
    public void openChooseProfessionMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§e⚒ §lChoisir un Métier §e⚒");

        fillWithGlass(gui);

        // Les 3 métiers
        gui.setItem(11, createProfessionChoiceItem("mineur"));
        gui.setItem(13, createProfessionChoiceItem("commercant"));
        gui.setItem(15, createProfessionChoiceItem("guerrier"));

        // Bouton retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§7← §lRetour");
        backMeta.setLore(Arrays.asList("§7Retour au menu métiers"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
        back.setItemMeta(backMeta);
        gui.setItem(22, back);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Gère les clics dans les menus des métiers
     */
    public void handleProfessionMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "view_talents" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    openTalentMenu(player, professionId, 0);
                }
            }
            case "change_profession" -> openChangeProfessionMenu(player);
            case "choose_profession" -> openChooseProfessionMenu(player);
            case "select_profession" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    if (plugin.getProfessionManager().setActiveProfession(player, professionId)) {
                        openProfessionMenu(player); // Rafraîchit le menu
                    }
                }
            }
            case "confirm_change" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    if (plugin.getProfessionManager().changeProfession(player, professionId)) {
                        openProfessionMenu(player); // Rafraîchit le menu
                    }
                }
            }
            case "upgrade_talent" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                String talentId = meta.getPersistentDataContainer().get(talentKey, PersistentDataType.STRING);
                Integer targetLevel = meta.getPersistentDataContainer().get(targetLevelKey, PersistentDataType.INTEGER);

                if (professionId != null && talentId != null && targetLevel != null) {
                    if (plugin.getProfessionManager().activateTalent(player, talentId, targetLevel)) {
                        // Rafraîchit la page des talents
                        String title = player.getOpenInventory().getTitle();
                        int page = title.contains("1-5") ? 0 : 1;
                        openTalentMenu(player, professionId, page);
                    }
                }
            }
            case "upgrade_kit" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                Integer targetLevel = meta.getPersistentDataContainer().get(targetLevelKey, PersistentDataType.INTEGER);

                if (professionId != null && targetLevel != null) {
                    if (plugin.getProfessionManager().activateKit(player, targetLevel)) { // CORRIGÉ: utilise activateKit
                        // Rafraîchit la page des talents
                        String title = player.getOpenInventory().getTitle();
                        int page = title.contains("1-5") ? 0 : 1;
                        openTalentMenu(player, professionId, page);
                    }
                }
            }
            case "prev_page" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    openTalentMenu(player, professionId, 0); // Page 1-5
                }
            }
            case "next_page" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    openTalentMenu(player, professionId, 1); // Page 6-10
                }
            }
            case "back_to_main" -> openProfessionMenu(player);
            case "close" -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            }
        }
    }

    /**
     * Remplit l'inventaire avec du verre coloré
     */
    private void fillWithGlass(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§7");
        filler.setItemMeta(meta);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    /**
     * Obtient le matériau correspondant à un métier
     */
    private Material getProfessionMaterial(String professionId) {
        return switch (professionId) {
            case "mineur" -> Material.DIAMOND_PICKAXE;
            case "commercant" -> Material.EMERALD;
            case "guerrier" -> Material.DIAMOND_SWORD;
            default -> Material.COMPASS;
        };
    }

    /**
     * Obtient le matériau correspondant à un talent
     */
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