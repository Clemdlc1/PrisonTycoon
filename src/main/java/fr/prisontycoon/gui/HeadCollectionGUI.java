package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.HeadCollectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour la collection de têtes
 */
public class HeadCollectionGUI {

    private final PrisonTycoon plugin;

    public HeadCollectionGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal de collection
     */
    public void openCollectionMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            player.sendMessage("§cErreur lors du chargement de vos données !");
            return;
        }

        int collectedCount = playerData.getCollectedHeads().size();
        int totalCount = plugin.getHeadCollectionManager().getTotalHeads();

        String title = "§8• §6Collection de Têtes §8(" + collectedCount + "/" + totalCount + ") •";
        Inventory gui = plugin.getGUIManager().createInventory(54, title);

        // Remplir avec du verre
        fillWithGlass(gui);

        // Item de statistiques (slot central haut)
        gui.setItem(13, createStatsItem(player, collectedCount, totalCount));

        // Récompenses - disposition sur 2 lignes
        Map<Integer, HeadCollectionManager.HeadReward> rewards = plugin.getHeadCollectionManager().getRewards();
        int[] rewardSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int slotIndex = 0;

        for (Map.Entry<Integer, HeadCollectionManager.HeadReward> entry : rewards.entrySet()) {
            if (slotIndex >= rewardSlots.length) break;

            gui.setItem(rewardSlots[slotIndex], createRewardItem(player, entry.getKey(), entry.getValue()));
            slotIndex++;
        }

        // Boutons de navigation
        gui.setItem(45, createInfoItem());
        gui.setItem(49, createCloseItem());
        gui.setItem(53, createRefreshItem());

        // Enregistrer et ouvrir
        plugin.getGUIManager().registerOpenGUI(player, GUIType.HEAD_COLLECTION, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
    }

    /**
     * Gère les clics dans le menu
     */
    public void handleCollectionMenuClick(Player player, int slot, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        String displayName = item.getItemMeta().hasDisplayName() && item.getItemMeta().displayName() != null
                ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName())
                : "";

        // Bouton fermer
        if (displayName.contains("§c✖ Fermer")) {
            player.closeInventory();
            return;
        }

        // Bouton actualiser
        if (displayName.contains("§a🔄 Actualiser")) {
            openCollectionMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }

        // Bouton info
        if (displayName.contains("§b📖 Comment ça marche")) {
            player.closeInventory();
            sendHelpMessage(player);
            return;
        }

        // Clic sur une récompense
        if (displayName.startsWith("§6🎁 Récompense")) {
            handleRewardClick(player, displayName);
        }
    }

    /**
     * Gère le clic sur une récompense
     */
    private void handleRewardClick(Player player, String displayName) {
        try {
            // Extraction du niveau de récompense depuis le nom
            String[] parts = displayName.split(" ");
            for (String part : parts) {
                if (part.contains("têtes)")) {
                    String numberStr = part.replace("(", "").replace("têtes)", "");
                    int rewardLevel = Integer.parseInt(numberStr);

                    PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    if (playerData == null) return;

                    if (plugin.getHeadCollectionManager().canClaimReward(player, rewardLevel)) {
                        if (plugin.getHeadCollectionManager().claimReward(player, rewardLevel)) {
                            // Rafraîchir le menu avec animation
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                            // Attendre un peu puis rafraîchir
                            Bukkit.getScheduler().runTaskLater(plugin, () -> openCollectionMenu(player), 20L); // 1 seconde
                        }
                    } else {
                        if (playerData.getClaimedHeadRewards().contains(rewardLevel)) {
                            player.sendMessage("§c❌ Vous avez déjà réclamé cette récompense !");
                        } else {
                            player.sendMessage("§c❌ Vous n'avez pas assez de têtes collectées !");
                            player.sendMessage("§7Il vous faut §e" + rewardLevel + " têtes§7, vous en avez §e" +
                                    playerData.getCollectedHeads().size() + "§7.");
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    break;
                }
            }
        } catch (NumberFormatException e) {
            plugin.getPluginLogger().debug("Erreur lors du parsing du niveau de récompense: " + displayName);
        }
    }

    /**
     * Crée l'item des statistiques
     */
    private ItemStack createStatsItem(Player player, int collected, int total) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        meta.setOwningPlayer(player);
        plugin.getGUIManager().applyName(meta, "§6§l📊 Vos Statistiques");

        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§e⭐ Têtes collectées: §f" + collected + "§8/§f" + total);

        if (total > 0) {
            double percentage = ((double) collected / total) * 100;
            lore.add("§e📈 Progression: §f" + String.format("%.1f", percentage) + "%");

            // Barre de progression visuelle
            int progressBars = (int) (percentage / 10); // 10 barres max
            StringBuilder progressBar = new StringBuilder("§8[");
            for (int i = 0; i < 10; i++) {
                if (i < progressBars) {
                    progressBar.append("§a█");
                } else {
                    progressBar.append("§7█");
                }
            }
            progressBar.append("§8]");
            lore.add(progressBar.toString());
        } else {
            lore.add("§e📈 Progression: §f0%");
            lore.add("§8[§7██████████§8]");
        }

        lore.add("§7");
        if (collected == total && total > 0) {
            lore.add("§a§l🎉 COLLECTION COMPLÈTE !");
            lore.add("§7Félicitations ! Vous avez trouvé");
            lore.add("§7toutes les têtes cachées !");
        } else {
            lore.add("§7Explorez le serveur pour trouver");
            lore.add("§7des têtes cachées et collectez-les");
            lore.add("§7avec un §eclic droit§7 !");
        }
        lore.add("§7");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item de récompense
     */
    private ItemStack createRewardItem(Player player, int requiredHeads, HeadCollectionManager.HeadReward reward) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean canClaim = plugin.getHeadCollectionManager().canClaimReward(player, requiredHeads);
        boolean alreadyClaimed = playerData.getClaimedHeadRewards().contains(requiredHeads);

        // Matériaux selon l'état et le type de récompense
        Material material = getRewardMaterial(reward.getType(), alreadyClaimed, canClaim);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String title = "§6🎁 Récompense " + requiredHeads + " §8(" + requiredHeads + " têtes)";
        plugin.getGUIManager().applyName(meta, title);

        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§e🎁 Type: §f" + getRewardTypeName(reward.getType()));
        lore.add("§e💰 Récompense: §f" + reward.getDescription());
        lore.add("§7");
        lore.add("§e📋 Requis: §f" + requiredHeads + " têtes");
        lore.add("§e👤 Vous avez: §f" + playerData.getCollectedHeads().size() + " têtes");
        lore.add("§7");

        if (alreadyClaimed) {
            lore.add("§a✓ §lDÉJÀ RÉCLAMÉ");
            lore.add("§7Cette récompense a été");
            lore.add("§7réclamée avec succès !");
        } else if (canClaim) {
            lore.add("§e⚡ §lCLIQUEZ POUR RÉCLAMER");
            lore.add("§7Vous pouvez réclamer cette");
            lore.add("§7récompense maintenant !");
        } else {
            lore.add("§c❌ §lPAS ASSEZ DE TÊTES");
            int missing = requiredHeads - playerData.getCollectedHeads().size();
            lore.add("§7Il vous manque §c" + missing + " tête" + (missing > 1 ? "s" : "") + "§7.");
        }

        lore.add("§7");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Récupère le matériau selon le type et l'état de la récompense
     */
    private Material getRewardMaterial(HeadCollectionManager.HeadRewardType type, boolean claimed, boolean canClaim) {
        if (claimed) {
            return Material.LIME_STAINED_GLASS_PANE; // Déjà réclamé = vert
        }

        if (canClaim) {
            // Peut réclamer = couleur selon le type
            return switch (type) {
                case BASIC -> Material.WHITE_STAINED_GLASS_PANE;
                case INTERMEDIATE -> Material.YELLOW_STAINED_GLASS_PANE;
                case ADVANCED -> Material.ORANGE_STAINED_GLASS_PANE;
                case RARE -> Material.PURPLE_STAINED_GLASS_PANE;
                case LEGENDARY -> Material.MAGENTA_STAINED_GLASS_PANE;
            };
        }

        // Ne peut pas réclamer = rouge
        return Material.RED_STAINED_GLASS_PANE;
    }

    /**
     * Récupère le nom affiché du type de récompense
     */
    private String getRewardTypeName(HeadCollectionManager.HeadRewardType type) {
        return switch (type) {
            case BASIC -> "§fBasique";
            case INTERMEDIATE -> "§eIntermédiaire";
            case ADVANCED -> "§6Avancé";
            case RARE -> "§5Rare";
            case LEGENDARY -> "§d§lLégendaire";
        };
    }

    /**
     * Remplit l'inventaire avec du verre coloré
     */
    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        plugin.getGUIManager().applyName(glassMeta, "§r");
        glass.setItemMeta(glassMeta);

        // Bordures
        for (int i = 0; i < 9; i++) gui.setItem(i, glass);
        for (int i = 45; i < 54; i++) gui.setItem(i, glass);
        for (int i = 9; i < 45; i += 9) {
            gui.setItem(i, glass);
            gui.setItem(i + 8, glass);
        }
    }

    /**
     * Crée le bouton fermer
     */
    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c✖ Fermer");
        List<String> lore = new ArrayList<>();
        lore.add("§7Ferme ce menu");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton actualiser
     */
    private ItemStack createRefreshItem() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§a🔄 Actualiser");
        List<String> lore = new ArrayList<>();
        lore.add("§7Actualise l'affichage de vos");
        lore.add("§7statistiques et récompenses");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton d'informations
     */
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§b📖 Comment ça marche ?");
        List<String> lore = new ArrayList<>();
        lore.add("§7Cliquez pour recevoir des");
        lore.add("§7explications sur la collection");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Envoie un message d'aide au joueur
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l📖 Collection de Têtes - Guide");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("§e🔍 §lComment trouver les têtes ?");
        player.sendMessage("§7• Des têtes sont cachées partout sur le serveur");
        player.sendMessage("§7• Elles émettent des §bparticules d'enchantement");
        player.sendMessage("§7• Explorez les différents mondes pour les trouver !");
        player.sendMessage("");
        player.sendMessage("§e⚡ §lComment les collecter ?");
        player.sendMessage("§7• Faites un §eclic droit§7 sur une tête pour la collecter");
        player.sendMessage("§7• Vous ne pouvez collecter chaque tête qu'§eune seule fois");
        player.sendMessage("§7• Votre progression est sauvegardée automatiquement");
        player.sendMessage("");
        player.sendMessage("§e🎁 §lRécompenses");
        player.sendMessage("§7• Plus vous collectez de têtes, plus les récompenses");
        player.sendMessage("§7  deviennent intéressantes !");
        player.sendMessage("§7• Utilisez §e/collection§7 pour réclamer vos récompenses");
        player.sendMessage("");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }
}