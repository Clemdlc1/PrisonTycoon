package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
import org.bukkit.Bukkit;
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

/**
 * Interface graphique pour les récompenses de métiers
 * 36 slots - 2 lignes de 5 pour les 10 niveaux de métier
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

        Inventory gui = Bukkit.createInventory(null, 36, "§6🎁 " + profession.getDisplayName() + " - Récompenses");

        fillWithGlass(gui);
        setupRewardsMenu(gui, player, profession);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    /**
     * Remplit l'inventaire avec du verre décoratif
     */
    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);

        // Remplit tous les slots sauf ceux utilisés
        for (int i = 0; i < gui.getSize(); i++) {
            if (!isUsedSlot(i)) {
                gui.setItem(i, glass);
            }
        }
    }

    /**
     * Vérifie si un slot est utilisé par le menu
     */
    private boolean isUsedSlot(int slot) {
        // Slots de récompenses
        for (int levelSlot : LEVEL_1_TO_5_SLOTS) {
            if (slot == levelSlot) return true;
        }
        for (int levelSlot : LEVEL_6_TO_10_SLOTS) {
            if (slot == levelSlot) return true;
        }
        // Slots spéciaux
        return slot == INFO_SLOT || slot == BACK_SLOT;
    }

    /**
     * Configure le menu des récompenses
     */
    private void setupRewardsMenu(Inventory gui, Player player, ProfessionManager.Profession profession) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Item d'information
        gui.setItem(INFO_SLOT, createInfoItem(player, profession));

        // Récompenses niveaux 1-5
        for (int i = 0; i < 5; i++) {
            int level = i + 1;
            int slot = LEVEL_1_TO_5_SLOTS[i];
            gui.setItem(slot, createRewardItem(player, profession, level));
        }

        // Récompenses niveaux 6-10
        for (int i = 0; i < 5; i++) {
            int level = i + 6;
            int slot = LEVEL_6_TO_10_SLOTS[i];
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
        int professionLevel = playerData.getProfessionLevel(profession.getId());

        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6📋 §lRécompenses " + profession.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7Réclamez vos récompenses de niveau !");
        lore.add("");
        lore.add("§7Votre niveau actuel: §e" + professionLevel + "/10");
        lore.add("§7Récompenses disponibles: §a" + getAvailableRewards(player, profession.getId()));
        lore.add("");
        lore.add("§7Les récompenses ne peuvent être");
        lore.add("§7réclamées qu'une seule fois.");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item de récompense pour un niveau
     */
    private ItemStack createRewardItem(Player player, ProfessionManager.Profession profession, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(profession.getId());
        boolean hasLevel = professionLevel >= level;
        boolean isClaimed = playerData.hasProfessionRewardClaimed(profession.getId(), level);

        // Matériau selon l'état
        Material material;
        if (isClaimed) {
            material = Material.BARRIER;
        } else if (hasLevel) {
            material = Material.EMERALD;
        } else {
            material = Material.COAL;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Nom selon l'état
        String color = isClaimed ? "§c" : (hasLevel ? "§a" : "§7");
        String status = isClaimed ? "✗" : (hasLevel ? "✓" : "⏳");
        meta.setDisplayName(color + status + " §fNiveau " + level + " - §6Récompense");

        List<String> lore = new ArrayList<>();
        lore.add("§7Récompense pour atteindre le niveau " + level);
        lore.add("");

        // Contenu de la récompense selon le niveau
        addRewardContent(lore, level);
        lore.add("");

        if (isClaimed) {
            lore.add("§c✗ Déjà réclamé");
        } else if (hasLevel) {
            lore.add("§a✓ Disponible !");
            lore.add("§e▶ Cliquez pour réclamer");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add("§7⏳ Atteignez le niveau " + level + " d'abord");
        }

        meta.setLore(lore);

        // Données pour le clic
        if (hasLevel && !isClaimed) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "claim_reward");
            meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, profession.getId());
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Ajoute le contenu de la récompense selon le niveau
     */
    private void addRewardContent(List<String> lore, int level) {
        switch (level) {
            case 1 -> {
                lore.add("§e• 10 000 Coins");
                lore.add("§b• 1 000 Tokens");
            }
            case 2 -> {
                lore.add("§e• 25 000 Coins");
                lore.add("§b• 2 500 Tokens");
                lore.add("§d• 1 Cristal Niveau 3");
            }
            case 3 -> {
                lore.add("§e• 50 000 Coins");
                lore.add("§b• 5 000 Tokens");
                lore.add("§6• 100 Beacons");
            }
            case 4 -> {
                lore.add("§e• 100 000 Coins");
                lore.add("§b• 10 000 Tokens");
                lore.add("§d• 1 Cristal Niveau 5");
            }
            case 5 -> {
                lore.add("§e• 200 000 Coins");
                lore.add("§b• 20 000 Tokens");
                lore.add("§6• 250 Beacons");
                lore.add("§c• 1 Clé Rare");
            }
            case 6 -> {
                lore.add("§e• 400 000 Coins");
                lore.add("§b• 40 000 Tokens");
                lore.add("§d• 1 Cristal Niveau 7");
            }
            case 7 -> {
                lore.add("§e• 800 000 Coins");
                lore.add("§b• 80 000 Tokens");
                lore.add("§6• 500 Beacons");
            }
            case 8 -> {
                lore.add("§e• 1 500 000 Coins");
                lore.add("§b• 150 000 Tokens");
                lore.add("§d• 1 Cristal Niveau 10");
                lore.add("§c• 1 Clé Épique");
            }
            case 9 -> {
                lore.add("§e• 3 000 000 Coins");
                lore.add("§b• 300 000 Tokens");
                lore.add("§6• 1 000 Beacons");
            }
            case 10 -> {
                lore.add("§e• 5 000 000 Coins");
                lore.add("§b• 500 000 Tokens");
                lore.add("§d• 1 Cristal Niveau 15");
                lore.add("§c• 1 Clé Légendaire");
                lore.add("§6• 2 000 Beacons");
            }
        }
    }

    /**
     * Crée le bouton de retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7← §lRetour");
        meta.setLore(Arrays.asList("§7Retour au menu des métiers", "", "§e▶ Cliquez pour retourner"));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_professions");

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gère les clics dans le menu des récompenses
     */
    public void handleRewardMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
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
                    // Rafraîchit le menu
                    openRewardsMenu(player, professionId);
                }
            }
            case "back_to_professions" -> {
                // Retour au menu principal des métiers
                plugin.getProfessionGUI().openProfessionMenu(player);
            }
        }
    }

    /**
     * Gère la réclamation d'une récompense
     */
    private void claimReward(Player player, String professionId, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifications de sécurité
        if (playerData.hasProfessionRewardClaimed(professionId, level)) {
            player.sendMessage("§cVous avez déjà réclamé cette récompense !");
            return;
        }

        if (playerData.getProfessionLevel(professionId) < level) {
            player.sendMessage("§cVous n'avez pas le niveau requis !");
            return;
        }

        // Marque comme réclamé
        playerData.claimProfessionReward(professionId, level);

        // Donne les récompenses
        giveRewardItems(player, level);

        // Sauvegarde
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Messages et effets
        player.sendMessage("§a✅ Récompense du niveau " + level + " réclamée !");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    /**
     * Donne physiquement les récompenses au joueur
     */
    private void giveRewardItems(Player player, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        switch (level) {
            case 1 -> {
                playerData.addCoins(10000);
                playerData.addTokens(1000);
                player.sendMessage("§7Reçu: §e+10 000 coins §7et §b+1 000 tokens");
            }
            case 2 -> {
                playerData.addCoins(25000);
                playerData.addTokens(2500);
                // Donner cristal niveau 3 (à implémenter si le système existe)
                player.sendMessage("§7Reçu: §e+25 000 coins§7, §b+2 500 tokens §7et §d1 cristal niveau 3");
            }
            case 3 -> {
                playerData.addCoins(50000);
                playerData.addTokens(5000);
                playerData.addBeacons(100);
                player.sendMessage("§7Reçu: §e+50 000 coins§7, §b+5 000 tokens §7et §6+100 beacons");
            }
            case 4 -> {
                playerData.addCoins(100000);
                playerData.addTokens(10000);
                player.sendMessage("§7Reçu: §e+100 000 coins§7, §b+10 000 tokens §7et §d1 cristal niveau 5");
            }
            case 5 -> {
                playerData.addCoins(200000);
                playerData.addTokens(20000);
                playerData.addBeacons(250);
                player.sendMessage("§7Reçu: §e+200 000 coins§7, §b+20 000 tokens§7, §6+250 beacons §7et §c1 clé rare");
            }
            case 6 -> {
                playerData.addCoins(400000);
                playerData.addTokens(40000);
                player.sendMessage("§7Reçu: §e+400 000 coins§7, §b+40 000 tokens §7et §d1 cristal niveau 7");
            }
            case 7 -> {
                playerData.addCoins(800000);
                playerData.addTokens(80000);
                playerData.addBeacons(500);
                player.sendMessage("§7Reçu: §e+800 000 coins§7, §b+80 000 tokens §7et §6+500 beacons");
            }
            case 8 -> {
                playerData.addCoins(1500000);
                playerData.addTokens(150000);
                player.sendMessage("§7Reçu: §e+1 500 000 coins§7, §b+150 000 tokens§7, §d1 cristal niveau 10 §7et §c1 clé épique");
            }
            case 9 -> {
                playerData.addCoins(3000000);
                playerData.addTokens(300000);
                playerData.addBeacons(1000);
                player.sendMessage("§7Reçu: §e+3 000 000 coins§7, §b+300 000 tokens §7et §6+1 000 beacons");
            }
            case 10 -> {
                playerData.addCoins(5000000);
                playerData.addTokens(500000);
                playerData.addBeacons(2000);
                player.sendMessage("§7Reçu: §e+5 000 000 coins§7, §b+500 000 tokens§7, §d1 cristal niveau 15§7, §c1 clé légendaire §7et §6+2 000 beacons");
            }
        }
    }

    /**
     * Compte le nombre de récompenses disponibles
     */
    private int getAvailableRewards(Player player, String professionId) {
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