package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.pets.PetDefinition;
import fr.prisontycoon.pets.PetRarity;
import fr.prisontycoon.pets.PetRegistry;
import fr.prisontycoon.pets.PetService.PetData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GUI Pets entièrement remanié avec fonctionnalités avancées :
 * - Affichage uniquement des pets possédés
 * - Tri par niveau de croissance (plus haut au plus bas)
 * - Lores améliorées avec informations détaillées
 * - Synergies affichées discrètement si présentes
 * - Meilleure organisation visuelle
 */
public class PetsMenuGUI {

    // Synergies définies
    private static final Map<String, SynergyInfo> SYNERGIES = new HashMap<>();

    static {
        SYNERGIES.put("commerce", new SynergyInfo(
                "Synergie Commerce",
                "§6✦ Commerce",
                Arrays.asList("fenrir", "licorne", "griffon"),
                "§6+0.20% Sell §7et §6+0.25% Tokens §7par croissance totale"
        ));
        SYNERGIES.put("savoirs", new SynergyInfo(
                "Synergie Savoirs",
                "§b✦ Savoirs",
                Arrays.asList("sphinx", "hippogriffe", "kelpie"),
                "§b+0.25% Pet XP §7et §b+0.20% XP Joueur §7par croissance totale"
        ));
        SYNERGIES.put("machineries", new SynergyInfo(
                "Synergie Machineries",
                "§8✦ Machineries",
                Arrays.asList("tarasque_royale", "tarasque", "blackshuck"),
                "§8+0.25% Efficacité autominer §7et §8-0.05% Usure pioche §7par croissance totale"
        ));
        SYNERGIES.put("richesses", new SynergyInfo(
                "Synergie Richesses",
                "§2✦ Richesses",
                Arrays.asList("basilic", "vouivre", "kraken"),
                "§2+0.20% Gain avant-poste §7et §2+0.10% Beacons §7par croissance totale"
        ));
        SYNERGIES.put("opportunites", new SynergyInfo(
                "Synergie Opportunités",
                "§d✦ Opportunités",
                Arrays.asList("morrigan", "cernunnos", "farfadet_r"),
                "§d+0.05% Proc enchants §7et §d+0.15% Chance clés §7par croissance totale"
        ));
    }

    private final PrisonTycoon plugin;
    private final NamespacedKey PET_ID_KEY;

    public PetsMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.PET_ID_KEY = new NamespacedKey(plugin, "pet_id");
    }

    public void openPetsMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§6🐾 §lMes Compagnons");

        // Remplir les bordures
        fillBorders(gui);

        // Obtenir les pets du joueur
        Map<String, PetData> playerPets = plugin.getPetService().getPlayerPets(player.getUniqueId());

        // Créer la section équipe (slots 10, 11, 12)
        createTeamSection(gui, player, playerPets);

        // Créer la section collection (pets possédés triés par croissance)
        createCollectionSection(gui, player, playerPets);

        // Ajouter les synergies si présentes
        addSynergiesIfActive(gui, player, playerPets);

        // Boutons de navigation
        addNavigationButtons(gui);

        // Statistiques générales
        addStatsSection(gui, playerPets);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.PETS_MENU, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.2f);
    }

    private void createTeamSection(Inventory gui, Player player, Map<String, PetData> playerPets) {
        // Slots d'équipe
        int[] teamSlots = {3, 5, 7};
        List<PetData> equippedPets = playerPets.values().stream()
                .filter(pd -> pd.equipped)
                .collect(Collectors.toList());

        for (int i = 0; i < teamSlots.length; i++) {
            if (i < getUnlockedSlots(player)) {
                if (i < equippedPets.size()) {
                    // Pet équipé
                    PetData petData = equippedPets.get(i);
                    PetDefinition def = PetRegistry.get(petData.id);
                    if (def != null) {
                        ItemStack petItem = createEquippedPetItem(def, petData);
                        gui.setItem(teamSlots[i], petItem);
                    }
                } else {
                    // Slot vide mais débloqué
                    ItemStack emptySlot = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                    ItemMeta meta = emptySlot.getItemMeta();
                    plugin.getGUIManager().applyName(meta, "§a✓ Slot libre §f#" + (i + 1));
                    plugin.getGUIManager().applyLore(meta, Arrays.asList(
                            "§7Équipez un compagnon pour remplir ce slot",
                            "§7Les compagnons équipés vous donnent leurs bonus"
                    ));
                    emptySlot.setItemMeta(meta);
                    gui.setItem(teamSlots[i], emptySlot);
                }
            } else {
                // Slot verrouillé
                ItemStack lockedSlot = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = lockedSlot.getItemMeta();
                plugin.getGUIManager().applyName(meta, "§c✖ Slot verrouillé §f#" + (i + 1));
                plugin.getGUIManager().applyLore(meta, Arrays.asList(
                        "§8Se débloquera via les quêtes",
                        "§8Progression future du serveur"
                ));
                lockedSlot.setItemMeta(meta);
                gui.setItem(teamSlots[i], lockedSlot);
            }
        }
    }

    private void createCollectionSection(Inventory gui, Player player, Map<String, PetData> playerPets) {
        if (playerPets.isEmpty()) {
            // Aucun pet possédé
            ItemStack noPets = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPets.getItemMeta();
            plugin.getGUIManager().applyName(meta, "§c✖ Aucun compagnon");
            plugin.getGUIManager().applyLore(meta, Arrays.asList(
                    "§7Vous ne possédez aucun compagnon pour le moment",
                    "§7Ouvrez des §eboîtes de compagnons §7pour en obtenir !"
            ));
            noPets.setItemMeta(meta);
            gui.setItem(22, noPets);
            return;
        }

        // Trier les pets par croissance (décroissant) et filtrer: ne pas montrer ceux déjà équipés
        List<PetData> sortedPets = playerPets.values().stream()
                .filter(pd -> !pd.equipped)
                .sorted((a, b) -> Integer.compare(b.growth, a.growth))
                .collect(Collectors.toList());

        // Afficher les pets dans la collection (lignes 3-6)
        int[] collectionSlots = {
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43,
                46, 47, 48, 49, 50, 51, 52
        };

        for (int i = 0; i < Math.min(sortedPets.size(), collectionSlots.length); i++) {
            PetData petData = sortedPets.get(i);
            PetDefinition def = PetRegistry.get(petData.id);
            if (def != null) {
                ItemStack petItem = createCollectionPetItem(def, petData);
                gui.setItem(collectionSlots[i], petItem);
            }
        }
    }

    private ItemStack createEquippedPetItem(PetDefinition def, PetData petData) {
        ItemStack head = plugin.getPetService().getHeadFor(def);
        ItemMeta meta = head.getItemMeta();

        String rarityColor = getRarityColor(def.rarity());
        plugin.getGUIManager().applyName(meta, rarityColor + "✦ " + def.displayName() + " §8(Équipé)");

        double totalBonus = def.basePerGrowthPercent() * petData.growth;
        int xpPerLevel = (int) Math.max(1, Math.round(100 * def.rarity().getXpScale()));
        long xpToNext = Math.max(0, xpPerLevel - (petData.xp % xpPerLevel));

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Rareté: " + rarityColor + def.rarity().name());
        lore.add("§7Effet: §f" + getEffectDisplayName(def.effectType().name()));
        lore.add("§7Bonus: §a+" + String.format("%.2f", totalBonus) + "%");
        lore.add("");
        lore.add("§7📊 Progression:");
        lore.add("  §7• Croissance: §e" + petData.growth + "§7/§e50");
        if (petData.growth < 50) {
            lore.add("  §7• XP: §d" + (petData.xp % xpPerLevel) + "§7/§d" + xpPerLevel + " §8(+" + xpToNext + " pour croissance)");
        } else {
            lore.add("  §7• XP: §6MAX §8(Croissance maximale atteinte)");
        }
        lore.add("");
        lore.add("§c👥 §lÉquipé - Clic pour retirer");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        // Stocker l'id du pet pour éviter les ambiguïtés (Tarasque vs Tarasque Royale)
        meta.getPersistentDataContainer().set(PET_ID_KEY, PersistentDataType.STRING, def.id());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createCollectionPetItem(PetDefinition def, PetData petData) {
        ItemStack head = plugin.getPetService().getHeadFor(def);
        ItemMeta meta = head.getItemMeta();

        String rarityColor = getRarityColor(def.rarity());
        String statusIcon = petData.equipped ? "§a⚡" : "§7◆";
        plugin.getGUIManager().applyName(meta, statusIcon + " " + rarityColor + def.displayName());

        double totalBonus = def.basePerGrowthPercent() * petData.growth;
        int xpPerLevel = (int) Math.max(1, Math.round(100 * def.rarity().getXpScale()));

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Rareté: " + rarityColor + def.rarity().name());
        lore.add("§7Effet: §f" + getEffectDisplayName(def.effectType().name()));
        lore.add("§7Bonus actuel: §a+" + String.format("%.2f", totalBonus) + "%");
        lore.add("§7Base/croissance: §e+" + def.basePerGrowthPercent() + "%");
        lore.add("");
        lore.add("§7📊 Statistiques:");
        lore.add("  §7• Croissance: §e" + petData.growth + "§7/§e50");
        lore.add("  §7• XP: §d" + (petData.xp % xpPerLevel) + "§7/§d" + xpPerLevel);
        lore.add("");

        if (petData.equipped) {
            lore.add("§a✓ Équipé - §cClic pour retirer");
        } else {
            lore.add("§e✦ Disponible - §aClic pour équiper");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        // Stocker l'id du pet pour éviter les ambiguïtés (Tarasque vs Tarasque Royale)
        meta.getPersistentDataContainer().set(PET_ID_KEY, PersistentDataType.STRING, def.id());
        head.setItemMeta(meta);
        return head;
    }

    private void addSynergiesIfActive(Inventory gui, Player player, Map<String, PetData> playerPets) {
        List<String> activeSynergies = getActiveSynergies(playerPets);

        if (!activeSynergies.isEmpty()) {
            // Affichage discret des synergies actives
            ItemStack synergyItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = synergyItem.getItemMeta();
            plugin.getGUIManager().applyName(meta, "§d✨ §lSynergie Active");

            List<String> lore = new ArrayList<>();
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§7Vos compagnons travaillent en synergie et vous conferent les bonus suivants:");
            lore.add("");

            int totalGrowth = playerPets.values().stream()
                    .filter(pd -> pd.equipped)
                    .mapToInt(pd -> pd.growth)
                    .sum();

            for (String synergyKey : activeSynergies) {
                SynergyInfo info = SYNERGIES.get(synergyKey);
                if (info != null) {
                    lore.add("§d▶ " + info.displayName);
                    lore.add("  " + info.description);
                    lore.add("  §7Puissance: §e" + totalGrowth + " §7(croissances combinées)");
                    lore.add("");
                }
            }
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            plugin.getGUIManager().applyLore(meta, lore);
            synergyItem.setItemMeta(meta);
            gui.setItem(49, synergyItem);
        }
    }

    private void addStatsSection(Inventory gui, Map<String, PetData> playerPets) {
        ItemStack stats = new ItemStack(Material.BOOK);
        ItemMeta meta = stats.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§b📊 §lStatistiques");

        int totalPets = playerPets.size();
        int equippedPets = (int) playerPets.values().stream().filter(pd -> pd.equipped).count();
        int totalGrowth = playerPets.values().stream().mapToInt(pd -> pd.growth).sum();
        long totalXP = playerPets.values().stream().mapToLong(pd -> pd.xp).sum();

        Map<PetRarity, Long> rarityCount = playerPets.values().stream()
                .collect(Collectors.groupingBy(
                        pd -> {
                            PetDefinition def = PetRegistry.get(pd.id);
                            return def != null ? def.rarity() : PetRarity.COMMON;
                        },
                        Collectors.counting()
                ));

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7📈 Vue d'ensemble:");
        lore.add("  §7• Compagnons possédés: §e" + totalPets + "§7/§e" + PetRegistry.all().size());
        lore.add("  §7• Compagnons équipés: §a" + equippedPets + "§7/§a3");
        lore.add("  §7• Croissance totale: §6" + totalGrowth);
        lore.add("  §7• XP totale accumulée: §d" + totalXP);
        lore.add("");
        lore.add("§7🎨 Par rareté:");
        lore.add("  §f• Communs: §7" + rarityCount.getOrDefault(PetRarity.COMMON, 0L));
        lore.add("  §5• Rares: §7" + rarityCount.getOrDefault(PetRarity.RARE, 0L));
        lore.add("  §d• Épiques: §7" + rarityCount.getOrDefault(PetRarity.EPIC, 0L));
        lore.add("  §6• Mythiques: §7" + rarityCount.getOrDefault(PetRarity.MYTHIC, 0L));
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        stats.setItemMeta(meta);
        gui.setItem(53, stats);
    }

    private void addNavigationButtons(Inventory gui) {
        // Bouton retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        plugin.getGUIManager().applyName(backMeta, "§c← §lRetour");
        plugin.getGUIManager().applyLore(backMeta, List.of("§7Retourner au menu principal"));
        back.setItemMeta(backMeta);
        gui.setItem(45, back);

        // Bouton aide
        ItemStack help = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta helpMeta = help.getItemMeta();
        plugin.getGUIManager().applyName(helpMeta, "§e❓ §lAide");
        plugin.getGUIManager().applyLore(helpMeta, Arrays.asList(
                "§7Comment utiliser le système de compagnons:",
                "§7• Ouvrez des §eboîtes de compagnons §7pour obtenir des compagnons",
                "§7• Équipez jusqu'à 3 compagnons pour obtenir leurs bonus",
                "§7• Nourrissez vos compagnons pour augmenter leur XP",
                "§7• Certaines combinaisons créent des §dsynergies§7 !",
                "",
                "§8Les compagnons vous suivent visuellement quand équipés"
        ));
        help.setItemMeta(helpMeta);
        gui.setItem(53, help);
    }

    public void handlePetsMenuClick(Player player, int slot, ItemStack item) {
        if (item == null) return;

        // Bouton retour
        if (slot == 45 && item.getType() == Material.ARROW) {
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }

        // Clic sur un pet (équipe ou collection) OU application de nourriture glissée depuis l'inventaire
        if (item.getItemMeta() != null && item.getType() == Material.PLAYER_HEAD) {
            // Priorité: lire l'id exact depuis le PDC
            String petId = item.getItemMeta().getPersistentDataContainer().get(PET_ID_KEY, PersistentDataType.STRING);
            if (petId == null && item.getItemMeta().hasDisplayName()) {
                String displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(item.getItemMeta().displayName());
                petId = extractPetIdFromDisplayName(displayName);
            }

            if (petId != null) {
                // Si le joueur tient dans la souris une nourriture de pet, on l'applique au pet cliqué
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && plugin.getPetService().isPetFood(cursor)) {
                    boolean applied = plugin.getPetService().applyFoodToPet(player, petId, cursor);
                    if (applied) {
                        openPetsMenu(player);
                    }
                    return;
                }
                boolean success = plugin.getPetService().toggleEquip(player, petId);
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
                    openPetsMenu(player); // Refresh GUI
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
                    player.sendMessage("§c✖ Impossible d'équiper ce compagnon (slots pleins ou compagnon non possédé)");
                }
            }
        }
    }

    // Utility methods
    private void fillBorders(Inventory gui) {
        ItemStack border = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6");
        border.setItemMeta(meta);

        // Bordures haut et bas
        for (int i = 0; i < 18; i++) {
            gui.setItem(i, border); // Éviter le titre équipe
        }
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, border); // Éviter les boutons
        }

        // Bordures côtés
        gui.setItem(26, border);
        gui.setItem(27, border);
        gui.setItem(35, border);
        gui.setItem(36, border);
        gui.setItem(44, border);
    }

    private String getRarityColor(PetRarity rarity) {
        return switch (rarity) {
            case COMMON -> "§f";
            case RARE -> "§5";
            case EPIC -> "§d";
            case MYTHIC -> "§6";
        };
    }

    private String getEffectDisplayName(String effectType) {
        return switch (effectType.toLowerCase()) {
            case "player_xp" -> "XP Joueur";
            case "money_greed" -> "Gains Argent";
            case "pet_xp" -> "XP Pets";
            case "fortune" -> "Fortune";
            case "pickaxe_wear" -> "Usure Pioche";
            case "job_xp" -> "XP Métier";
            case "outpost_gain" -> "Gains Avant-poste";
            case "keys_chance" -> "Chance Clés";
            case "tokens" -> "Tokens";
            case "autominer_fuel_consumption" -> "Consommation Autominer";
            case "proc_pickaxe" -> "Proc Enchants";
            case "sell" -> "Vente";
            case "beacons" -> "Beacons";
            default -> effectType;
        };
    }

    private List<String> getActiveSynergies(Map<String, PetData> playerPets) {
        List<String> equippedIds = playerPets.values().stream()
                .filter(pd -> pd.equipped)
                .map(pd -> pd.id)
                .collect(Collectors.toList());

        if (equippedIds.size() != 3) return new ArrayList<>();

        List<String> activeSynergies = new ArrayList<>();
        for (Map.Entry<String, SynergyInfo> entry : SYNERGIES.entrySet()) {
            if (equippedIds.containsAll(entry.getValue().requiredPets)) {
                activeSynergies.add(entry.getKey());
            }
        }
        return activeSynergies;
    }

    private String extractPetIdFromDisplayName(String displayName) {
        if (displayName == null) return null;
        String cleaned = displayName.replaceAll("§[0-9a-fk-or]", "").trim();

        // Supprimer les préfixes comme "⚡", "◆", "✦"
        cleaned = cleaned.replaceAll("^[⚡◆✦] ", "");

        for (PetDefinition def : PetRegistry.all()) {
            String defCleaned = def.displayName().replaceAll("§[0-9a-fk-or]", "").trim();
            if (cleaned.contains(defCleaned)) {
                return def.id();
            }
        }
        return null;
    }

    private int getUnlockedSlots(Player player) {
        return plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getUnlockedPetSlots();
    }

    // Classes utilitaires
        private record SynergyInfo(String name, String displayName, List<String> requiredPets, String description) {
    }
}