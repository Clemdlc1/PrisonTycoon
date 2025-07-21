package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.commands.PrestigeCommand;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeReward;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface graphique REFONTE pour le système de prestige
 * - Talents et récompenses dans le même menu
 * - Organisation en lignes par niveau, colonnes par type
 * - Pages dynamiques selon le prestige
 * - Système de réinitialisation des talents
 */
public class PrestigeGUI {

    // Slots du menu principal - NOUVEAU LAYOUT
    private static final int PRESTIGE_INFO_SLOT = 4;
    private static final int COMBINED_BUTTON_SLOT = 15; // Talents & Récompenses
    private static final int RESET_TALENTS_SLOT = 14; // Réinitialiser talents
    private static final int PERFORM_PRESTIGE_SLOT = 11;
    private static final int HELP_SLOT = 9;
    private static final int CLOSE_SLOT = 26;

    // Layout du menu talents/récompenses (54 slots)
    // 5 prestiges par page, 3 slots par prestige (colonnes)
    private static final int[] PRESTIGE_ROWS = {3, 12, 21, 30, 39}; // 5 lignes

    // Navigation
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int BACK_SLOT = 49;

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey prestigeLevelKey;
    private final NamespacedKey rewardIdKey;
    private final NamespacedKey talentKey;

    private static final long RESET_CONFIRMATION_TIMEOUT = 30000; // 30 secondes
    private final Map<UUID, Integer> currentPages = new ConcurrentHashMap<>();


    public PrestigeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "prestige_action");
        this.prestigeLevelKey = new NamespacedKey(plugin, "prestige_level");
        this.rewardIdKey = new NamespacedKey(plugin, "reward_id");
        this.talentKey = new NamespacedKey(plugin, "talent_name");
    }

    /**
     * Ouvre le menu principal du prestige
     */
    public void openMainPrestigeMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6🏆 §lSystème de Prestige §6🏆");

        fillWithGlass(gui);
        setupMainPrestigeMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);
    }

    /**
     * Configure le menu principal du prestige
     */
    private void setupMainPrestigeMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Informations de prestige au centre-haut
        gui.setItem(PRESTIGE_INFO_SLOT, createPrestigeInfoItem(player));

        // Bouton principal : Talents & Récompenses combinés avec compteurs
        gui.setItem(COMBINED_BUTTON_SLOT, createCombinedButton(player));

        // Bouton réinitialisation des talents
        gui.setItem(RESET_TALENTS_SLOT, createResetTalentsButton(player));

        // Bouton de prestige (si possible)
        if (plugin.getPrestigeManager().canPrestige(player)) {
            gui.setItem(PERFORM_PRESTIGE_SLOT, createPerformPrestigeButton(playerData.getPrestigeLevel() + 1));
        } else {
            gui.setItem(PERFORM_PRESTIGE_SLOT, createLockedPrestigeButton());
        }

        // Navigation
        gui.setItem(HELP_SLOT, createHelpItem());
        gui.setItem(CLOSE_SLOT, createCloseItem());
    }

    /**
     * Ouvre le menu combiné talents/récompenses avec pages dynamiques
     */
    public void openCombinedMenu(Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int maxPrestige = playerData.getPrestigeLevel();

        // 54 slots, 5 prestiges par page
        int maxPage = (maxPrestige - 1) / 5;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory gui = Bukkit.createInventory(null, 54, "§6🏆 Progression Prestige : P" + (page * 5 + 1) + "-P" + Math.min((page + 1) * 5, maxPrestige));

        fillWithGlass(gui);
        setupProgressionMenu(gui, player, page);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);
    }

    private void setupProgressionMenu(Inventory gui, Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int maxPrestige = playerData.getPrestigeLevel();

        // 5 lignes de prestige par page
        for (int i = 0; i < 5; i++) {
            int prestigeLevel = page * 5 + i + 1;
            if (prestigeLevel > 50) break; // Max P50

            int baseSlot = PRESTIGE_ROWS[i];
            setupPrestigeRow(gui, player, prestigeLevel, baseSlot);
        }

        // Navigation
        if (page > 0) {
            gui.setItem(PREV_PAGE_SLOT, createPageButton("§c⬅ Page précédente", page - 1));
        }

        int maxPage = (Math.min(maxPrestige, 50) - 1) / 5;
        if (page < maxPage) {
            gui.setItem(NEXT_PAGE_SLOT, createPageButton("§aPage suivante ➡", page + 1));
        }

        gui.setItem(BACK_SLOT, createBackToMainButton());
    }

    /**
     * Configure une ligne de récompenses spéciales (P5, P10, etc.)
     */
    private void setupSpecialRewardRow(Inventory gui, Player player, int prestigeLevel, int baseSlot, boolean isUnlocked) {
        List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);

        if (rewards.size() == 1) {
            // Récompense unique (P10, P20, etc.) - centrer sur la colonne du milieu
            PrestigeReward reward = rewards.get(0);
            gui.setItem(baseSlot + 1, createRewardItem(player, reward, isUnlocked));
        } else {
            // Choix multiple (P5, P15, etc.) - étaler sur les 3 colonnes
            for (int col = 0; col < Math.min(3, rewards.size()); col++) {
                PrestigeReward reward = rewards.get(col);
                gui.setItem(baseSlot + col, createRewardItem(player, reward, isUnlocked));
            }
        }
    }

    private int[] calculateCenteredSlots(int baseSlot, int count) {
        switch (count) {
            case 1:
                return new int[]{baseSlot + 1}; // Centre
            case 2:
                return new int[]{baseSlot, baseSlot + 2}; // Gauche et droite
            case 3:
            default:
                return new int[]{baseSlot, baseSlot + 1, baseSlot + 2}; // Les trois
        }
    }

    /**
     * Crée un item de récompense amélioré
     */
    // ==================== DIFFÉRENCIATION VISUELLE DES TALENTS DANS LE MENU PROGRESSION ====================

    private void setupTalentRow(Inventory gui, Player player, int prestigeLevel, int baseSlot, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Obtenir le talent pour ce niveau (un seul selon le cycle)
        PrestigeTalent talent = PrestigeTalent.getTalentForPrestige(prestigeLevel);
        if (talent == null) return;

        // Vérifier si ce talent a été choisi pour ce niveau
        String chosenTalentName = playerData.getChosenPrestigeTalent(prestigeLevel);
        boolean isChosen = talent.name().equals(chosenTalentName);
        boolean hasAnyChoice = chosenTalentName != null;

        // Séparer la description en colonnes (utilise les \n existants)
        String[] bonusLines = talent.getDescription().split("\\n");

        // Afficher chaque bonus sur une colonne différente
        for (int col = 0; col < Math.min(3, bonusLines.length); col++) {
            ItemStack item = createDifferentiatedTalentColumnItem(player, talent, prestigeLevel,
                    bonusLines[col], col, isUnlocked, isChosen, hasAnyChoice);
            gui.setItem(baseSlot + col, item);
        }
    }

    // Nouvelle méthode pour créer les items de colonne différenciés :
    private ItemStack createDifferentiatedTalentColumnItem(Player player, PrestigeTalent talent, int prestigeLevel,
                                                           String bonusDescription, int column, boolean isUnlocked,
                                                           boolean isChosen, boolean hasAnyChoice) {
        // Déterminer le matériau selon l'état
        Material material;
        if (isChosen) {
            material = getTalentColumnMaterial(talent, column, true); // Version brillante
        } else if (hasAnyChoice) {
            material = Material.GRAY_STAINED_GLASS_PANE; // Bloqué
        } else if (isUnlocked) {
            material = getTalentColumnMaterial(talent, column, false); // Version normale
        } else {
            material = Material.BLACK_STAINED_GLASS_PANE; // Verrouillé
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Déterminer l'état et la couleur
            String prefix;
            String nameColor;
            List<String> statusLore = new ArrayList<>();

            if (isChosen) {
                // TALENT CHOISI - Vert brillant
                prefix = "§a✅ ";
                nameColor = "§a§l";
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a✨ BONUS ACTIF ✨");
                statusLore.add("§7Ce bonus est appliqué à votre compte");
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                // Ajouter enchantement pour effet brillant
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            } else if (hasAnyChoice) {
                // AUTRE TALENT DÉJÀ CHOISI - Gris bloqué
                prefix = "§8✗ ";
                nameColor = "§8";
                statusLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§c❌ NON DISPONIBLE");
                statusLore.add("§7Ce talent a été choisi pour P" + prestigeLevel);
                statusLore.add("§7mais un autre bonus a été sélectionné.");
                statusLore.add("§7");
                statusLore.add("§7💡 Utilisez §e§lRéinitialiser Talents");
                statusLore.add("§7pour pouvoir rechoisir (500 beacons)");
                statusLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            } else if (isUnlocked) {
                // TALENT DISPONIBLE - Jaune/Or sélectionnable
                prefix = "§e⭘ ";
                nameColor = "§e§l";
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§6🌟 DISPONIBLE");
                statusLore.add("§7Vous pouvez sélectionner ce talent");
                statusLore.add("§7pour le niveau P" + prestigeLevel + ".");
                statusLore.add("§7");
                statusLore.add("§c⚠ Attention: §7Choix définitif!");
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a➤ Cliquez pour sélectionner");

                // Ajouter les données pour le clic
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_talent");
                meta.getPersistentDataContainer().set(prestigeLevelKey, PersistentDataType.INTEGER, prestigeLevel);
                meta.getPersistentDataContainer().set(talentKey, PersistentDataType.STRING, talent.name());

            } else {
                // TALENT VERROUILLÉ - Rouge bloqué
                prefix = "§c🔒 ";
                nameColor = "§c";
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§4❌ VERROUILLÉ");
                statusLore.add("§7Atteignez le niveau §6P" + prestigeLevel);
                statusLore.add("§7pour débloquer ce talent.");
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            }

            // Construire le nom avec le bonus spécifique
            String columnName = getColumnNameFromDescription(bonusDescription);
            meta.setDisplayName(prefix + nameColor + columnName + " §7(P" + prestigeLevel + ")");

            // Construire la lore complète
            List<String> lore = new ArrayList<>();

            // Description du bonus spécifique
            lore.add("§f" + bonusDescription.replace("§6", "§e").replace("§b", "§a"));
            lore.add("");

            // Nom du talent complet
            lore.add("§7Talent: §e" + talent.getDisplayName());
            lore.add("");

            // Statut avec couleurs
            lore.addAll(statusLore);

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private Material getTalentColumnMaterial(PrestigeTalent talent, int column, boolean isActive) {
        Material baseMaterial = switch (talent) {
            case PROFIT_AMELIORE, PROFIT_AMELIORE_II -> switch (column) {
                case 0 -> Material.GOLD_NUGGET; // Money Greed
                case 1 -> Material.EMERALD; // Prix vente
                case 2 -> Material.BEACON; // Gain avant-poste
                default -> Material.GOLD_INGOT;
            };
            case ECONOMIE_OPTIMISEE, ECONOMIE_OPTIMISEE_II -> switch (column) {
                case 0 -> Material.DIAMOND; // Token Greed
                case 1 -> Material.REDSTONE; // Taxe
                case 2 -> Material.IRON_SWORD; // Prix marchand PvP
                default -> Material.DIAMOND;
            };
        };

        // Version améliorée si le talent est actif
        if (isActive) {
            return switch (baseMaterial) {
                case GOLD_NUGGET -> Material.GOLD_INGOT;
                case EMERALD -> Material.EMERALD_BLOCK;
                case DIAMOND -> Material.DIAMOND_BLOCK;
                case REDSTONE -> Material.REDSTONE_BLOCK;
                case IRON_SWORD -> Material.NETHERITE_SWORD;
                default -> baseMaterial;
            };
        }

        return baseMaterial;
    }

    // Méthode pour extraire le nom de la colonne depuis la description :
    private String getColumnNameFromDescription(String description) {
        // Nettoyer les codes couleur et extraire le nom principal
        String clean = description.replaceAll("§[0-9a-fk-or]", "").trim();

        if (clean.contains("Money Greed")) return "Money Greed";
        if (clean.contains("Token Greed")) return "Token Greed";
        if (clean.contains("Prix de vente") || clean.contains("Prix vente")) return "Prix de Vente";
        if (clean.contains("Gain avant-poste") || clean.contains("avant-poste")) return "Avant-Poste";
        if (clean.contains("Taxe")) return "Réduction Taxe";
        if (clean.contains("marchand PvP")) return "Marchand PvP";
        if (clean.contains("Effet")) return "Effet Multiplié";

        // Fallback : prendre les premiers mots
        String[] words = clean.split(" ");
        return words.length > 2 ? words[0] + " " + words[1] : clean;
    }

    // Nouvelle méthode pour créer un item résumé de talent :
    private ItemStack createTalentSummaryItem(int prestigeLevel, PrestigeTalent chosenTalent) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6📋 Résumé P" + prestigeLevel);

            List<String> lore = new ArrayList<>();
            lore.add("§7Talent sélectionné pour ce niveau:");
            lore.add("");
            lore.add("§a✅ " + chosenTalent.getDisplayName());
            lore.add("§7" + chosenTalent.getDescription());
            lore.add("");
            lore.add("§7Les bonus de ce talent sont");
            lore.add("§7actuellement §aactifs §7sur votre compte.");
            lore.add("");
            lore.add("§8Utilisez la réinitialisation des talents");
            lore.add("§8pour modifier votre choix.");

            meta.setLore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }

        return item;
    }

    // Améliorer l'affichage des récompenses spéciales également :
    private ItemStack createRewardItem(Player player, PrestigeReward reward, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        boolean isCompleted = playerData.hasChosenSpecialReward(reward.getId());

        Material material = isCompleted ? Material.CHEST : (isUnlocked ? Material.ENDER_CHEST : Material.BARRIER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String prefix;
            String nameColor;
            List<String> statusLore = new ArrayList<>();

            if (isCompleted) {
                // RÉCOMPENSE RÉCUPÉRÉE
                prefix = "§a✅ ";
                nameColor = "§a§l";
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a🎁 RÉCOMPENSE RÉCUPÉRÉE");
                statusLore.add("§7Cette récompense a déjà été");
                statusLore.add("§7réclamée et appliquée à votre compte.");
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            } else if (isUnlocked) {
                // RÉCOMPENSE DISPONIBLE
                prefix = "§e🎁 ";
                nameColor = "§e§l";
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§6🌟 RÉCOMPENSE DISPONIBLE");
                statusLore.add("§7Vous pouvez réclamer cette récompense");
                statusLore.add("§7spéciale de prestige gratuitement.");
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a➤ Cliquez pour réclamer");

                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "unlock_reward");
                meta.getPersistentDataContainer().set(rewardIdKey, PersistentDataType.STRING, reward.getId());

            } else {
                // RÉCOMPENSE VERROUILLÉE
                prefix = "§c🔒 ";
                nameColor = "§c";
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§4❌ RÉCOMPENSE VERROUILLÉE");

                // Extraire le niveau de prestige requis depuis l'ID
                String prestigeStr = reward.getId().replaceAll("[^0-9]", "");
                statusLore.add("§7Atteignez le niveau §6P" + prestigeStr);
                statusLore.add("§7pour débloquer cette récompense.");
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            }

            meta.setDisplayName(prefix + nameColor + reward.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§f" + reward.getDescription());
            lore.add("");

            // Détails de la récompense basés sur le type
            lore.add("§7🎁 Récompense:");
            lore.add("§7  • Type: §e" + formatRewardType(reward.getType()));
            if (reward.getValue() != null) {
                lore.add("§7  • Contenu: §e" + formatRewardValue(reward.getValue().toString()));
            }
            lore.add("");

            lore.addAll(statusLore);

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // Méthodes utilitaires pour formater les récompenses :
    private String formatRewardType(PrestigeReward.RewardType type) {
        return switch (type) {
            case TOKENS -> "Tokens";
            case KEYS -> "Clés";
            case CRYSTALS -> "Cristaux";
            case AUTOMINER -> "Autominer";
            case BOOK -> "Livre";
            case TITLE -> "Titre";
            case COSMETIC -> "Cosmétique";
            case BEACONS -> "Beacons";
            case ARMOR_SET -> "Set d'Armure";
            default -> type.name();
        };
    }

    private String formatRewardValue(String value) {
        // Formater la valeur de manière lisible
        if (value.contains(":")) {
            return value.replace(":", " x").replace(",", " + ");
        }
        return value;
    }

    // Méthode utilitaire pour formater les dates :
    private String formatDate(long timestamp) {
        if (timestamp == 0) return "Inconnu";

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
        return sdf.format(new java.util.Date(timestamp));
    }

    // Améliorer l'affichage de l'en-tête de niveau dans le menu :
    private ItemStack createPrestigeLevelHeader(int prestigeLevel, boolean isUnlocked, boolean hasContent) {
        Material material = hasContent ?
                (isUnlocked ? Material.DIAMOND : Material.IRON_INGOT) :
                Material.COAL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String color = isUnlocked ? "§6" : "§c";
            String status = isUnlocked ? "§a[DÉBLOQUÉ]" : "§c[VERROUILLÉ]";

            meta.setDisplayName(color + "§l━━━ PRESTIGE " + prestigeLevel + " ━━━ " + status);

            List<String> lore = new ArrayList<>();

            if (prestigeLevel % 5 == 0) {
                lore.add("§7🎁 Niveau de récompenses spéciales");
                lore.add("§7Réclamez des bonus permanents uniques!");
            } else {
                lore.add("§7⭐ Niveau de talents");
                lore.add("§7Choisissez un talent pour améliorer vos capacités!");
            }

            lore.add("");

            if (isUnlocked) {
                lore.add("§a✅ Niveau débloqué - Contenu disponible");
            } else {
                lore.add("§c❌ Atteignez P" + prestigeLevel + " pour débloquer");
            }

            meta.setLore(lore);

            if (isUnlocked && hasContent) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    // Modifier setupPrestigeRow pour inclure l'en-tête :
    private void setupPrestigeRow(Inventory gui, Player player, int prestigeLevel, int baseSlot) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean isUnlocked = prestigeLevel <= playerData.getPrestigeLevel();

        if (prestigeLevel % 5 == 0) {
            // Palier spécial : récompenses
            setupSpecialRewardRow(gui, player, prestigeLevel, baseSlot, isUnlocked);
        } else {
            // Palier normal : talents
            setupTalentRow(gui, player, prestigeLevel, baseSlot, isUnlocked);
        }
    }

    /**
     * Gère les clics dans le menu
     */
    public void handleMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "open_combined" -> openCombinedMenu(player, 0);
            case "reset_talents" -> handleTalentReset(player);
            case "perform_prestige" -> {
                if (plugin.getPrestigeManager().canPrestige(player)) {
                    plugin.getPrestigeManager().performPrestige(player);
                    openMainPrestigeMenu(player); // Refresh
                }
            }
            case "page_navigation" -> {
                Integer targetPage = meta.getPersistentDataContainer().get(NamespacedKey.fromString("page"), PersistentDataType.INTEGER);
                if (targetPage != null) {
                    openCombinedMenu(player, targetPage);
                }
            }
            case "unlock_reward" -> {
                String rewardId = meta.getPersistentDataContainer().get(rewardIdKey, PersistentDataType.STRING);
                if (rewardId != null) {
                    handleRewardUnlock(player, rewardId);
                }
            }
            case "choose_talent" -> {
                Integer prestigeLevel = meta.getPersistentDataContainer().get(prestigeLevelKey, PersistentDataType.INTEGER);
                String talentName = meta.getPersistentDataContainer().get(talentKey, PersistentDataType.STRING);
                if (prestigeLevel != null && talentName != null) {
                    handleTalentChoice(player, prestigeLevel, talentName);
                }
            }
            case "back_to_main" -> openMainPrestigeMenu(player);
        }
    }


    /**
     * Gère le déverrouillage d'une récompense (gratuit)
     */
    private void handleRewardUnlock(Player player, String rewardId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier si déjà réclamée
        if (playerData.hasChosenSpecialReward(rewardId)) {
            player.sendMessage("§c❌ Vous avez déjà réclamé cette récompense!");
            return;
        }

        // Récupérer la récompense
        PrestigeReward reward = findRewardById(rewardId);
        if (reward == null) {
            player.sendMessage("§c❌ Récompense introuvable!");
            return;
        }

        // Marquer comme choisie et débloquée
        playerData.addChosenSpecialReward(rewardId);
        playerData.unlockPrestigeReward(rewardId);

        // Donner la récompense
        plugin.getPrestigeManager().getRewardManager().giveSpecialReward(player, reward);

        // Messages et effets
        player.sendMessage("§a✅ Récompense débloquée : " + reward.getDisplayName());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        UUID playerId = player.getUniqueId();
        Integer currentPage = currentPages.getOrDefault(playerId, 0);
        // Sauvegarder
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Rafraîchir l'interface
        player.closeInventory();
        openCombinedMenu(player, currentPage);
    }

    /**
     * Gère le choix d'un talent (gratuit, un seul par niveau)
     */
    private void handleTalentChoice(Player player, int prestigeLevel, String talentName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier que le niveau est débloqué
        if (prestigeLevel > playerData.getPrestigeLevel()) {
            player.sendMessage("§c❌ Vous devez atteindre P" + prestigeLevel + " pour choisir ce talent!");
            return;
        }

        // Vérifier si un talent est déjà choisi pour ce niveau
        String existingTalent = playerData.getChosenPrestigeTalent(prestigeLevel);
        if (existingTalent != null) {
            player.sendMessage("§c❌ Vous avez déjà choisi un talent pour P" + prestigeLevel + "!");
            player.sendMessage("§7Utilisez la réinitialisation des talents pour rechoisir.");
            return;
        }

        // Choisir le talent
        playerData.choosePrestigeTalent(prestigeLevel, talentName);

        // Ajouter le talent aux talents actifs
        PrestigeTalent talent = PrestigeTalent.valueOf(talentName);
        playerData.addPrestigeTalent(talent);

        // Messages et effets
        player.sendMessage("§a✅ Talent choisi : " + talent.getDisplayName());
        player.sendMessage("§7Les bonus sont maintenant actifs!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        UUID playerId = player.getUniqueId();
        Integer currentPage = currentPages.getOrDefault(playerId, 0);

        // Sauvegarder
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Rafraîchir l'interface en maintenant la page
        player.closeInventory();
        openCombinedMenu(player, currentPage);
    }

    /**
     * Trouve une récompense par son ID
     */
    private PrestigeReward findRewardById(String rewardId) {
        // Extraire le niveau de prestige depuis l'ID (format: "p5_autominer", "p10_title", etc.)
        try {
            String levelStr = rewardId.substring(1, rewardId.indexOf("_"));
            int prestigeLevel = Integer.parseInt(levelStr);

            List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);

            for (PrestigeReward reward : rewards) {
                if (reward.getId().equals(rewardId)) {
                    return reward;
                }
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la recherche de récompense: " + rewardId);
        }

        return null;
    }


    /**
     * Gère la réinitialisation des talents
     */
    private void handleTalentReset(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérification des beacons
        if (playerData.getBeacons() < 500) {
            player.sendMessage("§c❌ Vous n'avez pas assez de beacons! Requis: §e500");
            player.sendMessage("§7Vous avez: §c" + playerData.getBeacons() + " beacons");
            return;
        }

        // Vérification qu'il y a des talents à réinitialiser
        if (playerData.getPrestigeTalents().isEmpty()) {
            player.sendMessage("§c❌ Vous n'avez aucun talent de prestige à réinitialiser!");
            return;
        }

        // NOUVEAU : Ajouter la confirmation en attente avec timestamp
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Obtenir l'instance de PrestigeCommand pour accéder à la Map
        PrestigeCommand prestigeCommand = (PrestigeCommand) plugin.getCommand("prestige").getExecutor();
        prestigeCommand.addPendingResetConfirmation(playerId, currentTime);

        // Confirmation avec chrono
        player.sendMessage("§6⚠ CONFIRMATION REQUISE ⚠");
        player.sendMessage("§7Cette action va:");
        player.sendMessage("§7• Réinitialiser TOUS vos talents de prestige");
        player.sendMessage("§7• Coûter §c500 beacons");
        player.sendMessage("§7• Les récompenses spéciales ne seront PAS récupérables");
        player.sendMessage("");
        player.sendMessage("§aTapez §e/prestige confirmer-reset §apour confirmer");
        player.sendMessage("§c⏰ Vous avez 30 secondes pour confirmer");

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 0.8f);

        // Programmer l'expiration automatique
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (prestigeCommand.removePendingResetConfirmation(playerId, currentTime)) {
                if (player.isOnline()) {
                    player.sendMessage("§c⏰ Délai de confirmation écoulé pour la réinitialisation des talents.");
                }
            }
        }, RESET_CONFIRMATION_TIMEOUT / 50); // Convertir ms en ticks
    }


    /**
     * Confirme la réinitialisation des talents (appelée depuis la commande)
     */
    public void confirmTalentReset(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier les beacons
        if (playerData.getBeacons() < 500) {
            player.sendMessage("§c❌ Vous n'avez pas assez de beacons! (500 requis)");
            return;
        }

        // Effectuer la réinitialisation
        playerData.removeBeacon(500);
        playerData.resetPrestigeTalents(); // Nouvelle méthode qui garde les récompenses

        // Messages et effets
        player.sendMessage("§a✅ Talents de prestige réinitialisés!");
        player.sendMessage("§7Coût: §c-500 beacons");
        player.sendMessage("§7Vos récompenses spéciales sont conservées");
        player.sendMessage("§7Vous pouvez maintenant rechoisir vos talents");

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Fermer le menu et rouvrir le principal
        player.closeInventory();
        openMainPrestigeMenu(player);
    }


    // =============== MÉTHODES DE CRÉATION D'ITEMS ===============

    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }
    }

    private ItemStack createPrestigeInfoItem(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        Material material = prestigeLevel > 0 ? Material.NETHER_STAR : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6🏆 Votre Prestige");

            List<String> lore = new ArrayList<>();
            lore.add("§7Niveau actuel: " + playerData.getPrestigeDisplayName());
            lore.add("");

            if (prestigeLevel > 0) {
                lore.add("§e⚡ Bonus actifs:");

                // Calculer les bonus totaux
                double moneyBonus = playerData.getPrestigeMoneyGreedBonus();
                double tokenBonus = playerData.getPrestigeTokenGreedBonus();
                double taxReduction = playerData.getPrestigeTaxReduction();
                double sellBonus = playerData.getPrestigeSellBonus();

                if (moneyBonus > 0) {
                    lore.add("§7  • §6Money Greed: §a+" + String.format("%.1f", moneyBonus * 100) + "%");
                }
                if (tokenBonus > 0) {
                    lore.add("§7  • §bToken Greed: §a+" + String.format("%.1f", tokenBonus * 100) + "%");
                }
                if (taxReduction > 0) {
                    lore.add("§7  • §cRéduction Taxe: §a-" + String.format("%.1f", taxReduction * 100) + "%");
                }
                if (sellBonus > 0) {
                    lore.add("§7  • §ePrix Vente: §a+" + String.format("%.1f", sellBonus * 100) + "%");
                }

                lore.add("");
                lore.add("§7Récompenses spéciales réclamées: §e" + playerData.getChosenSpecialRewards().size());
            } else {
                lore.add("§7Atteignez le prestige 1 pour débloquer des bonus!");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCombinedButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentPrestige = playerData.getPrestigeLevel();

        // Calculer les éléments disponibles
        int availableTalents = 0;
        int availableRewards = 0;
        int totalTalents = 0;
        int totalRewards = 0;

        for (int level = 1; level <= Math.max(currentPrestige, 5); level++) {
            if (level % 5 == 0) {
                // Niveau de récompenses
                totalRewards++;
                if (!playerData.hasChosenSpecialReward("p" + level + "_*") && level <= currentPrestige) {
                    // Vérifier s'il y a des récompenses non réclamées pour ce niveau
                    List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(level);
                    boolean hasUnclaimedReward = false;
                    for (PrestigeReward reward : rewards) {
                        if (!playerData.hasChosenSpecialReward(reward.getId())) {
                            hasUnclaimedReward = true;
                            break;
                        }
                    }
                    if (hasUnclaimedReward) {
                        availableRewards++;
                    }
                }
            } else {
                // Niveau de talents
                totalTalents++;
                String chosenTalent = playerData.getChosenPrestigeTalent(level);
                if (chosenTalent == null && level <= currentPrestige) {
                    availableTalents++;
                }
            }
        }

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e📚 Talents & Récompenses");

            List<String> lore = new ArrayList<>();
            lore.add("§7Gérez vos talents et récompenses");
            lore.add("§7de prestige dans un menu unifié");
            lore.add("");

            // Affichage des disponibilités
            if (availableTalents > 0) {
                lore.add("§a✨ " + availableTalents + " talent" + (availableTalents > 1 ? "s" : "") + " disponible" + (availableTalents > 1 ? "s" : ""));
            }
            if (availableRewards > 0) {
                lore.add("§a🎁 " + availableRewards + " récompense" + (availableRewards > 1 ? "s" : "") + " disponible" + (availableRewards > 1 ? "s" : ""));
            }

            if (availableTalents == 0 && availableRewards == 0) {
                lore.add("§7Aucun élément disponible");
            }

            lore.add("");
            lore.add("§7Progression:");
            lore.add("§7Talents: §e" + (totalTalents - availableTalents) + "§7/§e" + totalTalents);
            lore.add("§7Récompenses: §e" + (totalRewards - availableRewards) + "§7/§e" + totalRewards);
            lore.add("");
            lore.add("§eCliquez pour ouvrir!");

            if (availableTalents > 0 || availableRewards > 0) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_combined");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createResetTalentsButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean hasEnoughBeacons = playerData.getBeacons() >= 500;
        boolean hasTalents = !playerData.getPrestigeTalents().isEmpty();

        Material material = hasEnoughBeacons && hasTalents ? Material.TNT : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c🔄 Réinitialiser Talents");

            List<String> lore = new ArrayList<>();
            lore.add("§7Remet à zéro tous vos talents");
            lore.add("§7de prestige pour les réattribuer");
            lore.add("");
            lore.add("§7Coût: §e500 beacons");
            lore.add("§7Vos beacons: " + (hasEnoughBeacons ? "§a" : "§c") + playerData.getBeacons());
            lore.add("");

            if (!hasTalents) {
                lore.add("§cAucun talent à réinitialiser");
            } else if (!hasEnoughBeacons) {
                lore.add("§cBeacons insuffisants!");
            } else {
                lore.add("§7⚠ Les récompenses spéciales");
                lore.add("§7ne peuvent PAS être réclamées à nouveau");
                lore.add("");
                lore.add("§eCliquez pour réinitialiser");
            }

            meta.setLore(lore);

            if (hasEnoughBeacons && hasTalents) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "reset_talents");
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPerformPrestigeButton(int nextLevel) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6🚀 Effectuer Prestige " + nextLevel);
            meta.setLore(List.of(
                    "§7Passez au niveau de prestige suivant",
                    "§7et débloquez de nouveaux bonus!",
                    "",
                    "§aConditions remplies!",
                    "",
                    "§eCliquez pour prestigier!"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "perform_prestige");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createLockedPrestigeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c🔒 Prestige Verrouillé");
            meta.setLore(List.of(
                    "§7Vous devez remplir les conditions",
                    "§7pour effectuer un prestige",
                    "",
                    "§7Consultez §e/prestige info §7pour",
                    "§7voir les prérequis"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPageButton(String name, int targetPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of("§7Page " + (targetPage + 1)));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page_navigation");
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("page"), PersistentDataType.INTEGER, targetPage);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createBackToMainButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c← Retour au menu principal");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createBackToCombinedButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§a← Retour aux Talents & Récompenses");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page_navigation");
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("page"), PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e❓ Aide");
            meta.setLore(List.of(
                    "§7Le système de prestige vous permet",
                    "§7de recommencer avec des bonus permanents",
                    "",
                    "§7• Talents cycliques automatiques",
                    "§7• Récompenses spéciales tous les 5 niveaux",
                    "§7• Possibilité de réinitialiser les talents",
                    "",
                    "§7Plus d'infos: §e/prestige help"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c✗ Fermer");
            item.setItemMeta(meta);
        }

        return item;
    }

    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        currentPages.remove(playerId);
    }
}