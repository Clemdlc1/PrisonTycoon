package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.HeadEnum;
import fr.prisontycoon.utils.HeadUtils;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Interface graphique principale du système d'automineur
 */
public class AutominerGUI {

    private final PrisonTycoon plugin;

    public AutominerGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal des automineurs
     */
    public void openMainMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        Inventory inv = plugin.getGUIManager().createInventory(27, "§8Menu Automineurs");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.AUTOMINER_MAIN, inv);

        // Slots des automineurs actifs
        ItemStack slot1 = playerData.getActiveAutominerSlot1();
        ItemStack slot2 = playerData.getActiveAutominerSlot2();

        // Slot 1
        inv.setItem(10, Objects.requireNonNullElseGet(slot1, () -> createEmptySlotItem("§7Slot 1 - Vide", "§eCliquez avec un automineur", "§epour l'activer!")));

        // Slot 2
        inv.setItem(16, Objects.requireNonNullElseGet(slot2, () -> createEmptySlotItem("§7Slot 2 - Vide", "§eCliquez avec un automineur", "§epour l'activer!")));

        // Item Monde
        inv.setItem(12, createWorldItem(playerData));

        // Item Stockage
        inv.setItem(13, createStorageItem(playerData));

        // Item Carburant
        inv.setItem(14, createFuelItem(playerData));

        // Item Condensation
        inv.setItem(22, createCondensationItem());

        // Items décoratifs
        fillEmptySlots(inv);

        player.openInventory(inv);
    }

    /**
     * Gère les clics dans le menu principal
     */
    public void handleMainMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        switch (slot) {
            case 10: // Slot 1
                handleAutominerSlotClick(player, playerData, 1, clickType);
                break;
            case 16: // Slot 2
                handleAutominerSlotClick(player, playerData, 2, clickType);
                break;
            case 12: // Monde
                handleWorldClick(player, playerData, clickType);
                break;
            case 13: // Stockage
                if (clickType == ClickType.LEFT) {
                    plugin.getAutominerCondHeadGUI().openStorageMenu(player);
                }
                break;
            case 14: // Carburant
                if (clickType == ClickType.LEFT) {
                    plugin.getAutominerCondHeadGUI().openFuelMenu(player);
                }
                break;
            case 22: // Condensation
                if (clickType == ClickType.LEFT) {
                    plugin.getAutominerCondHeadGUI().openCondensationMenu(player);
                }
                break;
        }
    }

    /**
     * Gère l'interaction avec les items d'automineur depuis l'inventaire du joueur
     */
    public void handleInventoryItemClick(Player player, ItemStack item) {
        if (!plugin.getAutominerManager().isAutominer(item)) {
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Trouver le premier slot disponible
        if (playerData.getActiveAutominerSlot1() == null) {
            playerData.setActiveAutominerSlot1(item.clone());
            item.setAmount(0); // Retire l'item de l'inventaire
            player.sendMessage("§a✓ Automineur placé dans le slot 1!");
        } else if (playerData.getActiveAutominerSlot2() == null) {
            playerData.setActiveAutominerSlot2(item.clone());
            item.setAmount(0);
            player.sendMessage("§a✓ Automineur placé dans le slot 2!");
        } else {
            player.sendMessage("§cTous les slots d'automineur sont occupés!");
            return;
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        openMainMenu(player); // Rafraîchir le menu

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    // ==================== MÉTHODES PRIVÉES ====================

    private void handleAutominerSlotClick(Player player, PlayerData playerData, int slotNumber, ClickType clickType) {
        ItemStack currentAutominer = (slotNumber == 1) ?
                playerData.getActiveAutominerSlot1() :
                playerData.getActiveAutominerSlot2();

        if (currentAutominer == null) {
            player.sendMessage("§cAucun automineur dans ce slot!");
            return;
        }

        if (clickType == ClickType.LEFT) {
            // Ouvrir le menu d'amélioration
            plugin.getAutominerEnchantGUI().openEnchantMenu(player, currentAutominer, slotNumber);
        } else if (clickType == ClickType.SHIFT_LEFT) {
            // Retirer l'automineur
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage("§cVotre inventaire est plein!");
                return;
            }

            player.getInventory().addItem(currentAutominer);

            if (slotNumber == 1) {
                playerData.setActiveAutominerSlot1(null);
            } else {
                playerData.setActiveAutominerSlot2(null);
            }

            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
            player.sendMessage("§a✓ Automineur retiré du slot " + slotNumber + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            openMainMenu(player); // Rafraîchir le menu
        }
    }

    private void handleWorldClick(Player player, PlayerData playerData, ClickType clickType) {
        if (clickType == ClickType.SHIFT_LEFT) {
            // Générer un nouveau monde aléatoire
            String currentWorld = playerData.getAutominerCurrentWorld();
            String newWorld = generateRandomWorld(currentWorld);

            long beaconCost = calculateWorldChangeCost();
            if (playerData.getBeacons() < beaconCost) {
                player.sendMessage("§cVous n'avez pas assez de beacons! Coût: " + beaconCost);
                return;
            }

            playerData.removeBeacon(beaconCost);
            playerData.setAutominerCurrentWorld(newWorld);

            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            player.sendMessage("§a✓ Nouveau monde de minage: " + newWorld.toUpperCase());
            player.sendMessage("§7Coût: " + beaconCost + " beacons");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            openMainMenu(player); // Rafraîchir le menu
        }
    }

    private ItemStack createEmptySlotItem(String name, String... lore) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, name);
        if (lore.length > 0) plugin.getGUIManager().applyLore(meta, List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWorldItem(PlayerData playerData) {
        ItemStack item = HeadUtils.createHead(HeadEnum.GLOBE);
        ItemMeta meta = item.getItemMeta();

        String currentWorld = playerData.getAutominerCurrentWorld();
        if (currentWorld == null) {
            currentWorld = "mine-a";
            playerData.setAutominerCurrentWorld(currentWorld);
        }

        plugin.getGUIManager().applyName(meta, "§2🌍 Monde de Minage");
        List<String> lore = new ArrayList<>();
        lore.add("§7Monde actuel: §f" + currentWorld.toUpperCase());
        lore.add("");

        // Afficher la composition des blocs
        MineData mineData = plugin.getConfigManager().getMineData(currentWorld);
        if (mineData != null) {
            lore.add("§7Composition des blocs:");
            for (Map.Entry<Material, Double> entry : mineData.getBlockComposition().entrySet()) {
                String materialName = formatMaterialName(entry.getKey());
                double percentage = entry.getValue() * 100;

                // === CORRECTION : Obtenir le prix de vente avec nouvelle logique ===
                long sellPrice = plugin.getConfigManager().getSellPrice(entry.getKey());
                long blockCoins = 0;
                long blockTokens = 0;

                var blockValue = plugin.getConfigManager().getBlockValue(entry.getKey());
                if (blockValue != null) {
                    blockCoins = blockValue.coins();
                    blockTokens = blockValue.tokens();
                }

                String priceText = "";
                if (sellPrice > 0) {
                    priceText = " §7(§a" + NumberFormatter.format(sellPrice) + "§7$)";
                } else if (blockCoins > 0) {
                    priceText = " §7(§a" + NumberFormatter.format(blockCoins) + "§7$)";
                } else if (blockTokens > 0) {
                    priceText = " §7(§e" + NumberFormatter.format(blockTokens) + "§7T)";
                } else {
                    // Utiliser prix par défaut
                    long defaultPrice = getDefaultPrice(entry.getKey());
                    if (defaultPrice > 0) {
                        priceText = " §7(§a" + NumberFormatter.format(defaultPrice) + "§7$)";
                    }
                }

                lore.add("§8• §f" + materialName + ": §e" + String.format("%.1f%%", percentage) + priceText);
            }
        }

        lore.add("");
        lore.add("§eShift+Clic: §7Changer de monde");
        lore.add("§7Coût: §e" + calculateWorldChangeCost() + " beacons");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStorageItem(PlayerData playerData) {
        ItemStack item = HeadUtils.createHead(HeadEnum.CHEST_GUI);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6📦 Stockage");
        List<String> lore = new ArrayList<>();

        Map<Material, Long> storage = playerData.getAutominerStorageContents();
        long totalStored = storage.values().stream().mapToLong(Long::longValue).sum();
        long maxCapacity = 1000L + (playerData.getAutominerStorageLevel() * 500L);

        lore.add("§7Capacité: §f" + NumberFormatter.format(totalStored) + "§7/§f" + NumberFormatter.format(maxCapacity));
        lore.add("§7Niveau: §f" + playerData.getAutominerStorageLevel());
        lore.add("");

        if (storage.isEmpty()) {
            lore.add("§8Stockage vide");
        } else {
            lore.add("§7Contenu:");
            int count = 0;
            for (Map.Entry<Material, Long> entry : storage.entrySet()) {
                if (count >= 5) {
                    lore.add("§8... et " + (storage.size() - 5) + " autres");
                    break;
                }
                String materialName = entry.getKey().name().toLowerCase().replace("_", " ");
                lore.add("§8• §f" + materialName + ": §e" + NumberFormatter.format(entry.getValue()));
                count++;
            }
        }

        lore.add("");
        lore.add("§eClic: §7Ouvrir le stockage");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFuelItem(PlayerData playerData) {
        ItemStack item = HeadUtils.createHead(HeadEnum.FUEL);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§c⛽ Carburant");
        List<String> lore = new ArrayList<>();

        double fuelReserve = playerData.getAutominerFuelReserve();
        lore.add("§7Réserve: §f" + NumberFormatter.format((long) fuelReserve) + " têtes");

        // Calculer l'autonomie
        double totalConsumption = 0;
        ItemStack slot1 = playerData.getActiveAutominerSlot1();
        ItemStack slot2 = playerData.getActiveAutominerSlot2();

        if (slot1 != null) {
            totalConsumption += plugin.getAutominerManager().calculateFuelConsumption(slot1);
        }
        if (slot2 != null) {
            totalConsumption += plugin.getAutominerManager().calculateFuelConsumption(slot2);
        }

        if (totalConsumption > 0) {
            long autonomySeconds = (long) (fuelReserve / totalConsumption);
            lore.add("§7Autonomie: §f" + formatTime(autonomySeconds));
        } else {
            lore.add("§7Autonomie: §8Aucun automineur actif");
        }

        lore.add("");
        lore.add("§eClic: §7Ajouter du carburant");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCondensationItem() {
        ItemStack item = HeadUtils.createHead(HeadEnum.CRAFTING);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§d🔧 Condensation");
        List<String> lore = new ArrayList<>();
        lore.add("§7Fusionnez 9 automineurs identiques");
        lore.add("§7pour obtenir 1 du type supérieur");
        lore.add("");
        lore.add("§8Pierre → Fer → Or → Diamant → Émeraude → Beacon");
        lore.add("");
        lore.add("§eClic: §7Ouvrir la condensation");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fillEmptySlots(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        plugin.getGUIManager().applyName(fillerMeta, " ");
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private String generateRandomWorld(String currentWorld) {
        String[] worlds = {"mine-a", "mine-b", "mine-c", "mine-d", "mine-e", "mine-f", "mine-g", "mine-h",
                "mine-i", "mine-j", "mine-k", "mine-l", "mine-m", "mine-n", "mine-o", "mine-p",
                "mine-q", "mine-r", "mine-s", "mine-t", "mine-u", "mine-v", "mine-w", "mine-x",
                "mine-y", "mine-z"};

        String newWorld;
        do {
            newWorld = worlds[(int) (Math.random() * worlds.length)];
        } while (newWorld.equals(currentWorld));

        return newWorld;
    }

    private long calculateWorldChangeCost() {
        return 5; // 5 beacons par changement de monde
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "min " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "min";
        }
    }

    private String formatMaterialName(Material material) {
        return switch (material) {
            case COAL_ORE -> "Minerai de Charbon";
            case IRON_ORE -> "Minerai de Fer";
            case GOLD_ORE -> "Minerai d'Or";
            case DIAMOND_ORE -> "Minerai de Diamant";
            case EMERALD_ORE -> "Minerai d'Émeraude";
            case REDSTONE_ORE -> "Minerai de Redstone";
            case LAPIS_ORE -> "Minerai de Lapis-Lazuli";
            case ANCIENT_DEBRIS -> "Débris Antiques";
            case NETHERITE_BLOCK -> "Bloc de Netherite";
            case BEACON -> "Beacon";
            case STONE -> "Pierre";
            case COBBLESTONE -> "Pierre Taillée";
            default -> {
                String name = material.name().toLowerCase().replace("_", " ");
                String[] words = name.split("");
                StringBuilder formatted = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        formatted.append(Character.toUpperCase(word.charAt(0)))
                                .append(word.substring(1))
                                .append(" ");
                    }
                }
                yield formatted.toString().trim();
            }
        };
    }

    private long getDefaultPrice(Material material) {
        return switch (material) {
            case STONE, COBBLESTONE -> 1L;
            case COAL_ORE -> 5L;
            case IRON_ORE -> 10L;
            case GOLD_ORE -> 25L;
            case DIAMOND_ORE -> 100L;
            case EMERALD_ORE -> 150L;
            case REDSTONE_ORE -> 15L;
            case LAPIS_ORE -> 20L;
            case ANCIENT_DEBRIS -> 500L;
            case NETHERITE_BLOCK -> 1000L;
            case BEACON -> 2000L;
            default -> 1L;
        };
    }
}