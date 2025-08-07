package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface graphique pour les récompenses de métiers (CORRIGÉE)
 * - Gestionnaire de clics fonctionnel
 * - Logique de réclamation activée
 * - Rafraîchissement automatique du menu
 */
public class ProfessionRewardsGUI {

    private static final int[] LEVEL_1_TO_5_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] LEVEL_6_TO_10_SLOTS = {20, 21, 22, 23, 24};
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 31;
    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey professionKey;
    private final NamespacedKey levelKey;

    public ProfessionRewardsGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "reward_action");
        this.professionKey = new NamespacedKey(plugin, "profession_id");
        this.levelKey = new NamespacedKey(plugin, "level");
    }

    /**
     * Ouvre le menu des récompenses pour un métier
     */
    public void openRewardsMenu(Player player, String professionId) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return;

        Inventory gui = plugin.getGUIManager().createInventory(36, "§6🎁 " + profession.displayName() + " - Récompenses");
        // Enregistre le GUI avec des données contextuelles pour le retour
        plugin.getGUIManager().registerOpenGUI(player, GUIType.PROFESSION_REWARDS, gui, java.util.Map.of("professionId", professionId));

        fillWithGlass(gui);
        setupRewardsMenu(gui, player, profession);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    /**
     * NOUVEAU: Gère les clics dans le menu des récompenses
     */
    public void handleRewardsClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "claim_reward" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                Integer level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
                if (professionId != null && level != null) {
                    claimReward(player, professionId, level);
                }
            }
            case "back_to_professions" -> plugin.getProfessionGUI().openProfessionMenu(player);
        }
    }

    /**
     * Remplit l'inventaire avec du verre décoratif
     */
    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        plugin.getGUIManager().applyName(glassMeta, " ");
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }
    }

    /**
     * Configure le menu des récompenses
     */
    private void setupRewardsMenu(Inventory gui, Player player, ProfessionManager.Profession profession) {
        // Item d'information
        gui.setItem(INFO_SLOT, createInfoItem(player, profession));

        // Récompenses niveaux 1-5 et 6-10
        for (int i = 0; i < 10; i++) {
            int level = i + 1;
            int slot = (i < 5) ? LEVEL_1_TO_5_SLOTS[i] : LEVEL_6_TO_10_SLOTS[i - 5];
            gui.setItem(slot, createRewardItem(player, profession, level));
        }

        // Bouton retour
        gui.setItem(BACK_SLOT, createBackButton());
    }

    /**
     * Crée l'item d'information
     */
    private ItemStack createInfoItem(Player player, ProfessionManager.Profession profession) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(profession.id());

        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6📋 §lRécompenses " + profession.displayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7Réclamez vos récompenses de niveau !");
        lore.add("");
        lore.add("§7Votre niveau actuel: §e" + professionLevel + "/10");
        lore.add("§7Récompenses disponibles: §a" + getAvailableRewardsCount(player, profession.id()));
        lore.add("");
        lore.add("§7Les récompenses ne peuvent être");
        lore.add("§7réclamées qu'une seule fois.");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item de récompense pour un niveau
     */
    private ItemStack createRewardItem(Player player, ProfessionManager.Profession profession, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(profession.id());
        boolean hasLevel = professionLevel >= level;
        boolean isClaimed = playerData.hasProfessionRewardClaimed(profession.id(), level);

        Material material = isClaimed ? Material.BARRIER : (hasLevel ? Material.EMERALD : Material.COAL_BLOCK);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String color = isClaimed ? "§c" : (hasLevel ? "§a" : "§7");
        String status = isClaimed ? "✗" : (hasLevel ? "✓" : "⏳");
        plugin.getGUIManager().applyName(meta, color + status + " §fNiveau " + level + " - §6Récompense");

        List<String> lore = new ArrayList<>();
        lore.add("§7Récompense pour l'atteinte du niveau " + level + ".");
        lore.add("");

        addRewardContentLore(lore, level);
        lore.add("");

        if (isClaimed) {
            lore.add("§c✗ Déjà réclamé");
        } else if (hasLevel) {
            lore.add("§a✓ Disponible !");
            lore.add("§e▶ Cliquez pour réclamer");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

            // Données pour le clic
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "claim_reward");
            meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, profession.id());
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        } else {
            lore.add("§7⏳ Atteignez le niveau " + level + " d'abord");
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Ajoute la description de la récompense au lore
     */
    private void addRewardContentLore(List<String> lore, int level) {
        // Cette méthode reste identique à l'originale
        switch (level) {
            case 1 -> { lore.add("§e• 10 000 Coins"); lore.add("§b• 1 000 Tokens"); }
            case 2 -> { lore.add("§e• 25 000 Coins"); lore.add("§b• 2 500 Tokens"); lore.add("§d• 1 Cristal Niveau 3"); }
            case 3 -> { lore.add("§e• 50 000 Coins"); lore.add("§b• 5 000 Tokens"); lore.add("§6• 100 Beacons"); }
            case 4 -> { lore.add("§e• 100 000 Coins"); lore.add("§b• 10 000 Tokens"); lore.add("§d• 1 Cristal Niveau 5"); }
            case 5 -> { lore.add("§e• 200 000 Coins"); lore.add("§b• 20 000 Tokens"); lore.add("§6• 250 Beacons"); lore.add("§c• 1 Clé Rare"); }
            case 6 -> { lore.add("§e• 400 000 Coins"); lore.add("§b• 40 000 Tokens"); lore.add("§d• 1 Cristal Niveau 7"); }
            case 7 -> { lore.add("§e• 800 000 Coins"); lore.add("§b• 80 000 Tokens"); lore.add("§6• 500 Beacons"); }
            case 8 -> { lore.add("§e• 1 500 000 Coins"); lore.add("§b• 150 000 Tokens"); lore.add("§d• 1 Cristal Niveau 10"); lore.add("§c• 1 Clé Épique"); }
            case 9 -> { lore.add("§e• 3 000 000 Coins"); lore.add("§b• 300 000 Tokens"); lore.add("§6• 1 000 Beacons"); }
            case 10 -> { lore.add("§e• 5 000 000 Coins"); lore.add("§b• 500 000 Tokens"); lore.add("§d• 1 Cristal Niveau 15"); lore.add("§c• 1 Clé Légendaire"); lore.add("§6• 2 000 Beacons"); }
        }
    }

    /**
     * Crée le bouton de retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§7← §lRetour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retour au menu des métiers"));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_professions");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * CORRIGÉ: Gère la réclamation et la distribution d'une récompense
     */
    private void claimReward(Player player, String professionId, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.hasProfessionRewardClaimed(professionId, level) || playerData.getProfessionLevel(professionId) < level) {
            player.sendMessage("§cVous ne pouvez pas réclamer cette récompense.");
            return;
        }

        playerData.claimProfessionReward(professionId, level);

        // Distribution des récompenses (anciennement giveRewardItems)
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        switch (level) {
            case 1 -> { data.addCoins(10000); data.addTokens(1000); }
            case 2 -> { data.addCoins(25000); data.addTokens(2500); /* donner cristal */ }
            case 3 -> { data.addCoins(50000); data.addTokens(5000); data.addBeacons(100); }
            case 4 -> { data.addCoins(100000); data.addTokens(10000); /* donner cristal */ }
            case 5 -> { data.addCoins(200000); data.addTokens(20000); data.addBeacons(250); /* donner clé */ }
            case 6 -> { data.addCoins(400000); data.addTokens(40000); /* donner cristal */ }
            case 7 -> { data.addCoins(800000); data.addTokens(80000); data.addBeacons(500); }
            case 8 -> { data.addCoins(1500000); data.addTokens(150000); /* donner cristal, clé */ }
            case 9 -> { data.addCoins(3000000); data.addTokens(300000); data.addBeacons(1000); }
            case 10 -> { data.addCoins(5000000); data.addTokens(500000); data.addBeacons(2000); /* donner cristal, clé */ }
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ Récompense du niveau " + level + " réclamée !");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Rafraîchit le menu pour montrer que la récompense a été prise
        openRewardsMenu(player, professionId);
    }

    /**
     * Compte le nombre de récompenses disponibles
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
}