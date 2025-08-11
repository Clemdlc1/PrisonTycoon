package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeReward;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestionnaire des récompenses automatiques de prestige
 */
public class PrestigeRewardManager {

    private final PrisonTycoon plugin;

    public PrestigeRewardManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Donne les récompenses automatiques pour un niveau de prestige
     */
    public void giveAutomaticRewards(Player player, int prestigeLevel) {
        List<String> rewardsGiven = new ArrayList<>();

        // Tokens selon la tranche
        long tokens = calculateTokenReward(prestigeLevel);
        if (tokens > 0) {
            giveTokens(player, tokens);
            rewardsGiven.add("§6" + formatNumber(tokens) + " tokens");
        }

        // Clés selon la tranche
        String keyType = getKeyTypeForPrestige(prestigeLevel);
        if (keyType != null) {
            giveKey(player, keyType, 1);
            rewardsGiven.add("§b1 clé " + keyType);
        }

        // Cristaux vierges selon la tranche
        int[] crystalInfo = getCrystalInfoForPrestige(prestigeLevel);
        if (crystalInfo != null) {
            int level = crystalInfo[0];
            int count = crystalInfo[1];
            giveCrystals(player, level, count);
            rewardsGiven.add("§d" + count + " cristaux vierges niveau " + level);
        }

        // Autominers pour P21-30
        String autominerType = getAutominerForPrestige(prestigeLevel);
        if (autominerType != null) {
            giveAutominer(player, autominerType);
            rewardsGiven.add("§e1 autominer " + autominerType);
        }

        // Livres spéciaux pour P21-40
        String specialBook = getSpecialBookForPrestige(prestigeLevel);
        if (specialBook != null) {
            giveSpecialBook(player, specialBook);
            rewardsGiven.add("§5Livre " + specialBook);
        }

        // Beacons pour P41-50
        int beacons = getBeaconRewardForPrestige(prestigeLevel);
        if (beacons > 0) {
            giveBeacons(player, beacons);
            rewardsGiven.add("§a" + formatNumber(beacons) + " beacons");
        }

        // Set d'armure et épée pour P41-50
        if (prestigeLevel >= 41) {
            giveArmorAndSword(player, prestigeLevel);
            rewardsGiven.add("§cSet d'armure + épée prestiges");
        }

        // Message de récompenses reçues
        if (!rewardsGiven.isEmpty()) {
            player.sendMessage("§a📦 Récompenses automatiques reçues:");
            for (String reward : rewardsGiven) {
                player.sendMessage("§7• " + reward);
            }
        }
    }

    /**
     * Calcule les tokens à donner selon le niveau de prestige
     */
    private long calculateTokenReward(int prestigeLevel) {
        if (prestigeLevel >= 1 && prestigeLevel <= 10) {
            return 50000L * prestigeLevel; // 50k × niveau
        } else if (prestigeLevel >= 11 && prestigeLevel <= 20) {
            return 500000L * (prestigeLevel - 10); // 500k × (niveau-10)
        } else if (prestigeLevel >= 21 && prestigeLevel <= 30) {
            return 5000000L * (prestigeLevel - 20); // 5M × (niveau-20)
        } else if (prestigeLevel >= 31 && prestigeLevel <= 40) {
            return 25000000L * (prestigeLevel - 30); // 25M × (niveau-30)
        } else if (prestigeLevel >= 41 && prestigeLevel <= 50) {
            return 100000000L * (prestigeLevel - 40); // 100M × (niveau-40)
        }
        return 0;
    }

    /**
     * Obtient le type de clé pour un niveau de prestige
     */
    private String getKeyTypeForPrestige(int prestigeLevel) {
        if (prestigeLevel >= 1 && prestigeLevel <= 10) {
            return "rare";
        } else if (prestigeLevel >= 11 && prestigeLevel <= 20) {
            return "légendaire";
        } else if (prestigeLevel >= 21 && prestigeLevel <= 30) {
            return "cristal";
        }
        return null; // P31+ n'ont pas de clés automatiques
    }

    /**
     * Obtient les informations des cristaux vierges [niveau, quantité]
     */
    private int[] getCrystalInfoForPrestige(int prestigeLevel) {
        if (prestigeLevel >= 1 && prestigeLevel <= 10) {
            int level = 12 + (prestigeLevel - 1) % 5; // Niveaux 12-16
            return new int[]{level, 1};
        } else if (prestigeLevel >= 11 && prestigeLevel <= 20) {
            int level = 13 + (prestigeLevel - 11) % 5; // Niveaux 13-17
            return new int[]{level, 1};
        } else if (prestigeLevel >= 31 && prestigeLevel <= 40) {
            int level = 15 + (prestigeLevel - 31) % 5; // Niveaux 15-19
            return new int[]{level, 1};
        }
        return null;
    }

    /**
     * Obtient le type d'autominer pour certains prestiges
     */
    private String getAutominerForPrestige(int prestigeLevel) {
        return switch (prestigeLevel) {
            case 21 -> "Fer";
            case 23 -> "Diamant";
            case 25 -> "Emeraude";
            default -> null;
        };
    }

    /**
     * Obtient le livre spécial pour certains prestiges
     */
    private String getSpecialBookForPrestige(int prestigeLevel) {
        return switch (prestigeLevel) {
            case 25 -> "BeaconBreaker";
            case 27 -> "Tonnerre";
            case 35 -> "Bomber";
            case 40 -> "Incassable";
            default -> null;
        };
    }

    /**
     * Calcule les beacons pour P41-50
     */
    private int getBeaconRewardForPrestige(int prestigeLevel) {
        if (prestigeLevel >= 41 && prestigeLevel <= 50) {
            int factor = prestigeLevel - 40;
            return 1000 * factor * factor; // 1k × (niveau-40)²
        }
        return 0;
    }

    /**
     * Donne des tokens au joueur
     */
    private void giveTokens(Player player, long amount) {
        // TODO: Intégrer avec le système d'économie
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        // playerData.addTokens(amount);
        plugin.getPluginLogger().info("Tokens donnés à " + player.getName() + ": " + amount);
    }

    /**
     * Donne des clés au joueur
     */
    private void giveKey(Player player, String keyType, int amount) {
        // TODO: Intégrer avec le système de clés
        plugin.getPluginLogger().info("Clé donnée à " + player.getName() + ": " + amount + "x " + keyType);
    }

    /**
     * Donne des cristaux vierges au joueur
     */
    private void giveCrystals(Player player, int level, int count) {
        // TODO: Intégrer avec le système de cristaux
        plugin.getPluginLogger().info("Cristaux donnés à " + player.getName() + ": " + count + "x niveau " + level);
    }

    /**
     * Donne un autominer au joueur
     */
    private void giveAutominer(Player player, String type) {
        // TODO: Intégrer avec le système d'autominers
        plugin.getPluginLogger().info("Autominer donné à " + player.getName() + ": " + type);
    }

    /**
     * Donne un livre spécial au joueur
     */
    private void giveSpecialBook(Player player, String bookType) {
        // TODO: Intégrer avec le système de livres
        plugin.getPluginLogger().info("Livre spécial donné à " + player.getName() + ": " + bookType);
    }

    /**
     * Donne des beacons au joueur
     */
    private void giveBeacons(Player player, int amount) {
        ItemStack beacons = new ItemStack(Material.BEACON, amount);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(beacons);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), beacons);
            player.sendMessage("§e⚠ Inventaire plein! Les beacons ont été droppés au sol.");
        }
    }

    /**
     * Donne un set d'armure et une épée de prestige
     */
    private void giveArmorAndSword(Player player, int prestigeLevel) {
        // Création d'un set d'armure enchanté selon le niveau de prestige
        ItemStack helmet = createPrestigeArmor(Material.NETHERITE_HELMET, prestigeLevel);
        ItemStack chestplate = createPrestigeArmor(Material.NETHERITE_CHESTPLATE, prestigeLevel);
        ItemStack leggings = createPrestigeArmor(Material.NETHERITE_LEGGINGS, prestigeLevel);
        ItemStack boots = createPrestigeArmor(Material.NETHERITE_BOOTS, prestigeLevel);
        ItemStack sword = createPrestigeSword(prestigeLevel);

        // Donner les items
        giveOrDrop(player, helmet);
        giveOrDrop(player, chestplate);
        giveOrDrop(player, leggings);
        giveOrDrop(player, boots);
        giveOrDrop(player, sword);
    }

    /**
     * Crée une pièce d'armure de prestige
     */
    private ItemStack createPrestigeArmor(Material material, int prestigeLevel) {
        ItemStack armor = new ItemStack(material);
        ItemMeta meta = armor.getItemMeta();
        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§6Armure de Prestige P" + prestigeLevel);
            List<String> lore = new ArrayList<>();
            lore.add("§7Armure légendaire de prestige");
            lore.add("§6Niveau: P" + prestigeLevel);
            lore.add("§eEnchantements bonus selon le niveau");
            plugin.getGUIManager().applyLore(meta, lore);
            armor.setItemMeta(meta);
        }

        // TODO: Ajouter enchantements selon le niveau de prestige

        return armor;
    }

    /**
     * Crée une épée de prestige
     */
    private ItemStack createPrestigeSword(int prestigeLevel) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§6Épée de Prestige P" + prestigeLevel);
            List<String> lore = new ArrayList<>();
            lore.add("§7Épée légendaire de prestige");
            lore.add("§6Niveau: P" + prestigeLevel);
            lore.add("§eEnchantements bonus selon le niveau");
            plugin.getGUIManager().applyLore(meta, lore);
            sword.setItemMeta(meta);
        }

        // TODO: Ajouter enchantements selon le niveau de prestige

        return sword;
    }

    /**
     * Donne un item ou le drop si l'inventaire est plein
     */
    private void giveOrDrop(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    /**
     * Donne une récompense spéciale (choix P5, P10, etc.)
     */
    public void giveSpecialReward(Player player, PrestigeReward reward) {
        switch (reward.getType()) {
            case TOKENS -> {
                long amount = Long.parseLong(reward.getValue().toString());
                giveTokens(player, amount);
            }
            case KEYS -> {
                // Format: "crystal:2,legendary:3"
                String[] keyData = reward.getValue().toString().split(",");
                for (String keyInfo : keyData) {
                    String[] parts = keyInfo.split(":");
                    if (parts.length == 2) {
                        giveKey(player, parts[0], Integer.parseInt(parts[1]));
                    }
                }
            }
            case CRYSTALS -> {
                // Format: "18:3" (niveau:quantité)
                String[] crystalData = reward.getValue().toString().split(":");
                if (crystalData.length == 2) {
                    giveCrystals(player, Integer.parseInt(crystalData[0]), Integer.parseInt(crystalData[1]));
                }
            }
            case AUTOMINER -> giveAutominer(player, reward.getValue().toString());
            case BOOK -> {
                String bookData = reward.getValue().toString();
                if (bookData.startsWith("unique_random")) {
                    int count = bookData.contains(":") ? Integer.parseInt(bookData.split(":")[1]) : 1;
                    for (int i = 0; i < count; i++) {
                        giveSpecialBook(player, "unique_random");
                    }
                } else {
                    giveSpecialBook(player, bookData);
                }
            }
            case TITLE -> giveTitle(player, reward.getValue().toString());
            case COSMETIC -> giveCosmetic(player, reward.getValue().toString());
        }

        player.sendMessage("§a✅ Récompense spéciale reçue: §6" + reward.getDisplayName());
        plugin.getPluginLogger().info("Récompense spéciale donnée à " + player.getName() + ": " + reward.getId());
    }

    /**
     * Donne un titre au joueur
     */
    private void giveTitle(Player player, String title) {
        // TODO: Intégrer avec le système de titres
        plugin.getPluginLogger().info("Titre donné à " + player.getName() + ": " + title);
    }

    /**
     * Donne un cosmétique au joueur
     */
    private void giveCosmetic(Player player, String cosmetic) {
        // TODO: Intégrer avec le système de cosmétiques
        plugin.getPluginLogger().info("Cosmétique donné à " + player.getName() + ": " + cosmetic);
    }

    /**
     * Formate un nombre pour l'affichage
     */
    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}