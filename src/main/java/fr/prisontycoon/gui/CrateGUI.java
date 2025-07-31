package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.crates.CrateType;
import fr.prisontycoon.managers.CrateManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface graphique pour visualiser les récompenses des crates
 */
public class CrateGUI {

    private final PrisonTycoon plugin;
    private final CrateManager crateManager;
    private final DecimalFormat percentFormat;

    public CrateGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.percentFormat = new DecimalFormat("#0.0");

        plugin.getPluginLogger().info("§aCrateGUI initialisé.");
    }

    /**
     * Ouvre le GUI des récompenses pour un type de crate spécifique
     */
    public void openRewardsGUI(Player player, CrateType crateType) {

        // Crée l'inventaire
        String title = crateType.getColor() + "Récompenses - Crate " + crateType.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Ajoute les récompenses
        populateRewardsInventory(gui, crateType);

        addNavigationItems(player, gui, crateType);

        // Ouvre le GUI
        plugin.getGUIManager().registerOpenGUI(player, GUIType.CRATE_MANAGEMENT, gui);
        player.openInventory(gui);


        // Son d'ouverture
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);

        // Message informatif
        int keyCount = crateManager.countKeys(player, crateType.getDisplayName());
        player.sendMessage("§7Vous possédez §e" + keyCount + " §7clé(s) " +
                crateType.getColor() + crateType.getDisplayName());
    }

    /**
     * Remplit l'inventaire avec les récompenses du type de crate
     */
    private void populateRewardsInventory(Inventory gui, CrateType crateType) {
        List<CrateType.CrateReward> rewards = crateType.getAllRewards();

        int slot = 10; // Commence à partir du slot 10 pour un affichage centré

        for (CrateType.CrateReward reward : rewards) {
            // Évite de dépasser les limites de l'inventaire
            if (slot >= 44) break;

            // Saute les emplacements de bordure
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot += 2;
                continue;
            }

            ItemStack rewardDisplay = createRewardDisplayItem(reward, crateType);
            gui.setItem(slot, rewardDisplay);

            slot++;
        }
    }

    /**
     * Crée un item d'affichage pour une récompense avec toutes les informations
     */
    private ItemStack createRewardDisplayItem(CrateType.CrateReward reward, CrateType crateType) {
        ItemStack displayItem;

        // Détermine l'item d'affichage selon le type de récompense
        switch (reward.getType()) {
            case CONTAINER -> {
                displayItem = new ItemStack(Material.CHEST);
            }
            case KEY -> {
                displayItem = new ItemStack(Material.TRIPWIRE_HOOK);
            }
            case CRISTAL_VIERGE -> {
                displayItem = new ItemStack(Material.NETHER_STAR);
            }
            case LIVRE_UNIQUE -> {
                displayItem = new ItemStack(Material.ENCHANTED_BOOK);
            }
            case AUTOMINER -> {
                displayItem = new ItemStack(Material.IRON_PICKAXE);
            }
            case VOUCHER -> {
                displayItem = new ItemStack(Material.PAPER);
            }
            case BOOST -> {
                displayItem = new ItemStack(Material.POTION);
            }
            default -> {
                displayItem = new ItemStack(Material.BARRIER);
            }
        }

        // Configure les métadonnées
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;

        // Nom de l'item
        meta.setDisplayName(getRewardDisplayName(reward));

        // Lore détaillée
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Probabilité avec barre visuelle
        double probability = reward.getProbability();
        String probabilityBar = createProbabilityBar(probability);
        lore.add("§7Chance: §e" + percentFormat.format(probability) + "% " + probabilityBar);

        // Rareté
        String rarity = getRarityText(probability);
        lore.add("§7Rareté: " + rarity);

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Informations spécifiques selon le type
        addSpecificRewardInfo(lore, reward);

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Disponible dans: " + crateType.getColor() + "Crate " + crateType.getDisplayName());

        meta.setLore(lore);
        displayItem.setItemMeta(meta);

        return displayItem;
    }

    /**
     * Ajoute des informations spécifiques selon le type de récompense
     */
    private void addSpecificRewardInfo(List<String> lore, CrateType.CrateReward reward) {
        switch (reward.getType()) {
            case CONTAINER -> {
                lore.add("§7Tier du conteneur: §e" + reward.getContainerTier());
                lore.add("§7Permet de stocker des items");
            }
            case KEY -> {
                lore.add("§7Type de clé: §b" + reward.getKeyType());
                lore.add("§7Utilisable sur les crates correspondantes");
            }
            case LIVRE_UNIQUE -> {
                lore.add("§7Enchantement: §5" + reward.getBookType());
                lore.add("§7Applicable sur les outils");
            }
            case AUTOMINER -> {
                lore.add("§7Type: §7" + reward.getAutominerType().toUpperCase());
                lore.add("§7Mine automatiquement pour vous");
            }
            case BOOST -> {
                lore.add("§7Type: §c" + reward.getBoostType());
                lore.add("§7Multiplicateur: §cx" + reward.getBoostMultiplier());
                lore.add("§7Durée: §c" + formatDuration(reward.getBoostDuration()));
            }
            case VOUCHER -> {
                lore.add("§7Type: §e" + reward.getVoucherType());
                lore.add("§7Bonus temporaire activable");
            }
            case CRISTAL_VIERGE -> {
                lore.add("§7Cristal vierge à activer");
                lore.add("§7Niveau: §5" + reward.getCristaltLevel());
                lore.add("§7Applicable sur les outils");
            }
        }
    }

    /**
     * Crée une barre visuelle pour représenter la probabilité
     */
    private String createProbabilityBar(double probability) {
        int filledBars = (int) Math.round(probability / 5.0); // Barre sur 20 segments (100% / 5 = 20)
        StringBuilder bar = new StringBuilder("§8[");

        for (int i = 0; i < 20; i++) {
            if (i < filledBars) {
                if (probability >= 20) bar.append("§a█");
                else if (probability >= 10) bar.append("§e█");
                else if (probability >= 5) bar.append("§6█");
                else bar.append("§c█");
            } else {
                bar.append("§7█");
            }
        }

        bar.append("§8]");
        return bar.toString();
    }

    /**
     * Retourne le texte de rareté selon la probabilité
     */
    private String getRarityText(double probability) {
        if (probability >= 25) return "§aCommune";
        if (probability >= 15) return "§ePeu Commune";
        if (probability >= 8) return "§6Rare";
        if (probability >= 3) return "§5Épique";
        return "§dLégendaire";
    }

    /**
     * Obtient le nom d'affichage pour une récompense
     */
    private String getRewardDisplayName(CrateType.CrateReward reward) {
        switch (reward.getType()) {
            case CONTAINER -> {
                return "§e📦 Conteneur Tier " + reward.getContainerTier();
            }
            case KEY -> {
                String keyColor = switch (reward.getKeyType()) {
                    case "Cristal" -> "§d";
                    case "Légendaire" -> "§6";
                    case "Rare" -> "§5";
                    case "Peu Commune" -> "§9";
                    default -> "§f";
                };
                return keyColor + "🗝 Clé " + reward.getKeyType();
            }
            case CRISTAL_VIERGE -> {
                return "§d💎 Cristal Vierge";
            }
            case LIVRE_UNIQUE -> {
                return "§5📚 Livre " + reward.getBookType();
            }
            case AUTOMINER -> {
                return "§7⛏ Autominer " + reward.getAutominerType().toUpperCase();
            }
            case VOUCHER -> {
                return "§e🎫 Voucher " + reward.getVoucherType();
            }
            case BOOST -> {
                return "§c🚀 Boost " + reward.getBoostType();
            }
        }
        return "§fRécompense Inconnue";
    }

    /**
     * Ajoute les éléments de navigation au GUI
     */
    private void addNavigationItems(Player player, Inventory gui, CrateType crateType) {
        // Bordures décoratives
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);

        // Première et dernière ligne
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(45 + i, border);
        }

        // Côtés
        for (int i = 9; i < 45; i += 9) {
            gui.setItem(i, border);
            gui.setItem(i + 8, border);
        }

        // Informations de la crate
        ItemStack crateInfo = new ItemStack(Material.CHEST);
        ItemMeta crateInfoMeta = crateInfo.getItemMeta();
        crateInfoMeta.setDisplayName(crateType.getColor() + "Crate " + crateType.getDisplayName());

        List<String> crateInfoLore = new ArrayList<>();
        crateInfoLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        crateInfoLore.add("§7Tier: §e" + crateType.getTier());
        crateInfoLore.add("§7Récompenses: §e" + crateType.getAllRewards().size());

        // Calcul du total des probabilités
        double totalProbability = crateType.getAllRewards().stream()
                .mapToDouble(CrateType.CrateReward::getProbability)
                .sum();
        crateInfoLore.add("§7Total chances: §e" + percentFormat.format(totalProbability) + "%");

        crateInfoLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        crateInfoLore.add("§7Clés possédées: §e" +
                crateManager.countKeys(player, crateType.getDisplayName()));
        crateInfoLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        crateInfoLore.add("§cFermez ce menu pour retourner au jeu");

        crateInfoMeta.setLore(crateInfoLore);
        crateInfo.setItemMeta(crateInfoMeta);

        gui.setItem(49, crateInfo); // Slot central du bas

        // Bouton de fermeture
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c✖ Fermer");
        closeMeta.setLore(List.of("§7Cliquez pour fermer ce menu"));
        closeButton.setItemMeta(closeMeta);

        gui.setItem(53, closeButton);

        // Bouton d'aide
        ItemStack helpButton = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = helpButton.getItemMeta();
        helpMeta.setDisplayName("§e❓ Aide");

        List<String> helpLore = new ArrayList<>();
        helpLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        helpLore.add("§7Comment utiliser les crates:");
        helpLore.add("§e• §7Clic droit: Ouvre avec animation");
        helpLore.add("§e• §7Clic gauche: Affiche les récompenses");
        helpLore.add("§e• §7Shift + Clic droit: Ouvre toutes les clés");
        helpLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        helpLore.add("§7Les clés peuvent être dans votre");
        helpLore.add("§7inventaire ou dans vos conteneurs!");
        helpLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        helpMeta.setLore(helpLore);
        helpButton.setItemMeta(helpMeta);

        gui.setItem(45, helpButton);
    }

    /**
     * Formate une durée en secondes vers un texte lisible
     */
    private String formatDuration(int seconds) {
        if (seconds >= 3600) {
            int hours = seconds / 3600;
            int remainingMinutes = (seconds % 3600) / 60;
            return hours + "h" + (remainingMinutes > 0 ? remainingMinutes + "m" : "");
        } else if (seconds >= 60) {
            return (seconds / 60) + "min";
        } else {
            return seconds + "s";
        }
    }
}