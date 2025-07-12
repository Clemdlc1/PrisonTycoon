package fr.prisoncore.prisoncore.prisonTycoon.enchantments;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.data.BlockValueData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire des 18 enchantements custom
 * CORRIGÉ : Greeds avec chance, gains uniquement via Greeds, Escalator en mobilité
 * CORRECTION : Calcul coûts identique entre GUI et chat
 */
public class EnchantmentManager {

    private final PrisonTycoon plugin;
    private final Map<String, CustomEnchantment> enchantments;

    public EnchantmentManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantments = new HashMap<>();

        initializeEnchantments();
        plugin.getPluginLogger().info("§aEnchantmentManager initialisé avec " + enchantments.size() + " enchantements.");
    }

    /**
     * Initialise tous les enchantements custom
     */
    private void initializeEnchantments() {
        // CATÉGORIE ÉCONOMIQUE
        registerEnchantment(new TokenGreedEnchantment());
        registerEnchantment(new ExpGreedEnchantment());
        registerEnchantment(new MoneyGreedEnchantment());
        registerEnchantment(new KeyGreedEnchantment());
        registerEnchantment(new AbondanceEnchantment());
        registerEnchantment(new CombustionEnchantment());
        registerEnchantment(new PetXpEnchantment());

        // CATÉGORIE EFFICACITÉ
        registerEnchantment(new EfficiencyEnchantment());
        registerEnchantment(new FortuneEnchantment());
        registerEnchantment(new DurabilityEnchantment());

        // CATÉGORIE MOBILITÉ (Escalator déplacé ici)
        registerEnchantment(new NightVisionEnchantment());
        registerEnchantment(new SpeedEnchantment());
        registerEnchantment(new HasteEnchantment());
        registerEnchantment(new JumpBoostEnchantment());
        registerEnchantment(new EscalatorEnchantment());

        // CATÉGORIE SPÉCIAUX
        registerEnchantment(new LuckEnchantment());
        registerEnchantment(new LaserEnchantment());
        registerEnchantment(new ExplosionEnchantment());
    }

    /**
     * Enregistre un enchantement
     */
    private void registerEnchantment(CustomEnchantment enchantment) {
        enchantments.put(enchantment.getName(), enchantment);
    }

    /**
     * Obtient un enchantement par son nom
     */
    public CustomEnchantment getEnchantment(String name) {
        return enchantments.get(name);
    }

    /**
     * Obtient tous les enchantements
     */
    public Collection<CustomEnchantment> getAllEnchantments() {
        return enchantments.values();
    }

    /**
     * Obtient les enchantements par catégorie
     */
    public List<CustomEnchantment> getEnchantmentsByCategory(EnchantmentCategory category) {
        return enchantments.values().stream()
                .filter(ench -> ench.getCategory() == category)
                .sorted(Comparator.comparing(CustomEnchantment::getName))
                .toList();
    }

    /**
     * CORRIGÉ: Traite un bloc détruit - Gains UNIQUEMENT via Greeds
     */
    public void processBlockDestroyed(Player player, Location blockLocation, Material blockType, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute aux statistiques de minage
        playerData.addMinedBlock(blockType);

        // SUPPRIMÉ: Plus de gains directs via Fortune - uniquement via Greeds
        // applyFortuneGains(player, playerData, blockValue);

        // Teste chaque Greed sur ce bloc détruit avec chance
        processGreedEnchantments(player, playerData, blockType, blockLocation);

        // Teste les enchantements spéciaux
        processSpecialEnchantments(player, playerData, blockLocation, mineName);

        // Met à jour la combustion
        updateCombustion(playerData);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * NOUVEAU: Traite les enchantements Greed avec CHANCE et gains uniquement via Greeds
     */
    private void processGreedEnchantments(Player player, PlayerData playerData, Material blockType, Location blockLocation) {
        int luckLevel = playerData.getEnchantmentLevel("luck");
        double combustionMultiplier = playerData.getCombustionMultiplier();
        double abundanceMultiplier = playerData.isAbundanceActive() ? 2.0 : 1.0;

        // Base des gains sur la valeur du bloc
        BlockValueData blockValue = plugin.getConfigManager().getBlockValue(blockType);

        // Token Greed - CHANCE de se déclencher
        int tokenGreedLevel = playerData.getEnchantmentLevel("token_greed");
        if (tokenGreedLevel > 0) {
            double baseChance = 0.05; // 5% de base
            double luckBonus = luckLevel * 0.002; // +0.2% par niveau de Luck
            double totalChance = baseChance + luckBonus;

            if (ThreadLocalRandom.current().nextDouble() < totalChance) {
                // Gains basés sur la valeur du bloc ET le niveau de l'enchantement
                long blockTokens = blockValue.getTokens();
                long bonusTokens = Math.round((tokenGreedLevel * 5 + blockTokens * 2) * combustionMultiplier * abundanceMultiplier);

                playerData.addTokensViaPickaxe(bonusTokens);
                playerData.addGreedTrigger();

                // Envoie notification hotbar
                plugin.getScoreboardManager().sendHotbarGreedNotification(player, "Token Greed", bonusTokens, "tokens");
            }
        }

        // Exp Greed - CHANCE de se déclencher
        int expGreedLevel = playerData.getEnchantmentLevel("exp_greed");
        if (expGreedLevel > 0) {
            double baseChance = 0.05;
            double luckBonus = luckLevel * 0.002;
            double totalChance = baseChance + luckBonus;

            if (ThreadLocalRandom.current().nextDouble() < totalChance) {
                long blockExp = blockValue.getExperience();
                long bonusExp = Math.round((expGreedLevel * 50 + blockExp * 3) * combustionMultiplier * abundanceMultiplier);

                playerData.addExperienceViaPickaxe(bonusExp);
                playerData.addGreedTrigger();

                // Met à jour l'expérience vanilla
                plugin.getEconomyManager().initializeVanillaExp(player);

                // Envoie notification hotbar
                plugin.getScoreboardManager().sendHotbarGreedNotification(player, "Exp Greed", bonusExp, "XP");
            }
        }

        // Money Greed - CHANCE de se déclencher
        int moneyGreedLevel = playerData.getEnchantmentLevel("money_greed");
        if (moneyGreedLevel > 0) {
            double baseChance = 0.05;
            double luckBonus = luckLevel * 0.002;
            double totalChance = baseChance + luckBonus;

            if (ThreadLocalRandom.current().nextDouble() < totalChance) {
                long blockCoins = blockValue.getCoins();
                long bonusCoins = Math.round((moneyGreedLevel * 10 + blockCoins * 2) * combustionMultiplier * abundanceMultiplier);

                playerData.addCoinsViaPickaxe(bonusCoins);
                playerData.addGreedTrigger();

                // Envoie notification hotbar
                plugin.getScoreboardManager().sendHotbarGreedNotification(player, "Money Greed", bonusCoins, "coins");
            }
        }

        // Key Greed - chance fixe (pas affectée par Luck selon demande)
        int keyGreedLevel = playerData.getEnchantmentLevel("key_greed");
        if (keyGreedLevel > 0) {
            double chance = 0.01 * keyGreedLevel; // 1% par niveau
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                giveRandomKey(player);
                playerData.addKeyObtained();
            }
        }

        // Abundance (chance d'activation)
        int abundanceLevel = playerData.getEnchantmentLevel("abundance");
        if (abundanceLevel > 0 && !playerData.isAbundanceActive()) {
            double chance = 0.000001 * abundanceLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                playerData.activateAbundance(60000);
                player.sendMessage("§6🌟 ABONDANCE ACTIVÉE! §eGains doublés pendant 60 secondes!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            }
        }
    }

    /**
     * Applique les enchantements spéciaux (laser, explosion)
     */
    private void processSpecialEnchantments(Player player, PlayerData playerData,
                                            Location blockLocation, String mineName) {

        int laserLevel = playerData.getEnchantmentLevel("laser");
        int explosionLevel = playerData.getEnchantmentLevel("explosion");

        // Laser
        if (laserLevel > 0) {
            double chance = 0.00002 * laserLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateLaser(player, blockLocation, mineName);
            }
        }

        // Explosion
        if (explosionLevel > 0) {
            double chance = 0.0005 * explosionLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateExplosion(player, blockLocation, mineName);
            }
        }
    }

    /**
     * Met à jour la combustion
     */
    private void updateCombustion(PlayerData playerData) {
        int combustionLevel = playerData.getEnchantmentLevel("combustion");
        if (combustionLevel > 0) {
            int gainPerBlock = Math.max(1, combustionLevel / 10);
            playerData.updateCombustion(gainPerBlock);
        }
    }

    /**
     * Donne une clé aléatoire au joueur
     */
    private void giveRandomKey(Player player) {
        double rand = ThreadLocalRandom.current().nextDouble();
        String keyType;
        String keyColor;

        if (rand < 0.00005) {
            keyType = "Cristal";
            keyColor = "§d";
        } else if (rand < 0.00995) {
            keyType = "Légendaire";
            keyColor = "§6";
        } else if (rand < 0.09) {
            keyType = "Rare";
            keyColor = "§5";
        } else if (rand < 0.20) {
            keyType = "Peu Commune";
            keyColor = "§9";
        } else {
            keyType = "Commune";
            keyColor = "§f";
        }

        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta();
        meta.setDisplayName(keyColor + "Clé " + keyType);
        meta.setLore(Arrays.asList(
                "§7Clé de coffre " + keyColor + keyType,
                "§7Utilise cette clé pour ouvrir des coffres!",
                "§8Obtenue via Key Greed"
        ));
        key.setItemMeta(meta);

        player.getInventory().addItem(key);
        plugin.getScoreboardManager().sendHotbarGreedNotification(player, "Key Greed", 1, "clé " + keyColor + keyType);
    }

    /**
     * Active l'effet laser et traite chaque bloc détruit
     */
    private void activateLaser(Player player, Location start, String mineName) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return;

        var direction = player.getLocation().getDirection();
        int blocksDestroyed = 0;

        for (int i = 1; i <= 10; i++) {
            Location target = start.clone().add(direction.clone().multiply(i));

            if (!mineData.contains(target)) break;

            Material originalType = target.getBlock().getType();
            target.getBlock().setType(mineData.getRandomMaterial());
            blocksDestroyed++;

            // Traite chaque bloc détruit par le laser
            processBlockDestroyed(player, target, originalType, mineName);
        }

        if (blocksDestroyed > 0) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            playerData.addDestroyedBlocks(blocksDestroyed);
        }

        player.sendMessage("§c⚡ Laser activé! §e" + blocksDestroyed + " blocs détruits!");
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2.0f);
    }

    /**
     * Active l'effet explosion et traite chaque bloc détruit
     */
    private void activateExplosion(Player player, Location center, String mineName) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return;

        int radius = ThreadLocalRandom.current().nextInt(1, 4);
        int blocksDestroyed = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x*x + y*y + z*z <= radius*radius) {
                        Location target = center.clone().add(x, y, z);

                        if (mineData.contains(target)) {
                            Material originalType = target.getBlock().getType();
                            target.getBlock().setType(mineData.getRandomMaterial());
                            blocksDestroyed++;

                            // Traite chaque bloc détruit par l'explosion
                            processBlockDestroyed(player, target, originalType, mineName);
                        }
                    }
                }
            }
        }

        if (blocksDestroyed > 0) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            playerData.addDestroyedBlocks(blocksDestroyed);
        }

        player.sendMessage("§4💥 Explosion! §e" + blocksDestroyed + " blocs détruits!");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    /**
     * Calcule le coût d'amélioration d'un enchantement
     */
    public long getUpgradeCost(String enchantmentName, int currentLevel) {
        CustomEnchantment enchantment = getEnchantment(enchantmentName);
        if (enchantment == null) return 0;

        return enchantment.getUpgradeCost(currentLevel + 1);
    }

    /**
     * CORRIGÉ: Améliore un enchantement avec calcul de coûts identique au GUI
     */
    public boolean upgradeEnchantment(Player player, String enchantmentName, int requestedLevels) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        CustomEnchantment enchantment = getEnchantment(enchantmentName);

        if (enchantment == null) {
            plugin.getPluginLogger().warning("Enchantement introuvable: " + enchantmentName);
            return false;
        }

        int currentLevel = playerData.getEnchantmentLevel(enchantmentName);
        int maxPossibleLevels = enchantment.getMaxLevel() - currentLevel;

        if (maxPossibleLevels <= 0) {
            player.sendMessage("§cNiveau maximum déjà atteint!");
            return false;
        }

        long availableTokens = playerData.getTokens();

        // CORRECTION: Utilise la même logique que le GUI pour calculer les niveaux réellement achetables
        int actualLevels = 0;
        long totalCost = 0;

        for (int i = 1; i <= Math.min(requestedLevels, maxPossibleLevels); i++) {
            long cost = enchantment.getUpgradeCost(currentLevel + i);
            if (totalCost + cost <= availableTokens) {
                totalCost += cost;
                actualLevels = i;
            } else {
                break;
            }
        }

        if (actualLevels <= 0) {
            return false;
        }

        // Effectue l'amélioration
        if (playerData.removeTokens(totalCost)) {
            playerData.setEnchantmentLevel(enchantmentName, currentLevel + actualLevels);

            player.sendMessage("§a✅ " + enchantment.getDisplayName() + " amélioré de " + actualLevels +
                    " niveau" + (actualLevels > 1 ? "x" : "") + " au niveau " + (currentLevel + actualLevels) +
                    " §7(-" + NumberFormatter.format(totalCost) + " tokens)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
            return true;
        }

        return false;
    }

    /**
     * NOUVEAU: Vérifie si un joueur peut utiliser l'auto-upgrade
     */
    public boolean canUseAutoUpgrade(Player player) {
        return player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin");
    }
}

// Implémentations des enchantements avec Escalator déplacé
class TokenGreedEnchantment implements CustomEnchantment {
    public String getName() { return "token_greed"; }
    public String getDisplayName() { return "Token Greed"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Chance d'obtenir des tokens bonus"; }
    public int getMaxLevel() { return Integer.MAX_VALUE; }
    public long getUpgradeCost(int level) { return Math.round(5000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.GOLD_NUGGET; }
}

class ExpGreedEnchantment implements CustomEnchantment {
    public String getName() { return "exp_greed"; }
    public String getDisplayName() { return "Exp Greed"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Chance d'obtenir de l'expérience bonus"; }
    public int getMaxLevel() { return Integer.MAX_VALUE; }
    public long getUpgradeCost(int level) { return Math.round(3000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.EXPERIENCE_BOTTLE; }
}

class MoneyGreedEnchantment implements CustomEnchantment {
    public String getName() { return "money_greed"; }
    public String getDisplayName() { return "Money Greed"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Chance d'obtenir des coins bonus"; }
    public int getMaxLevel() { return Integer.MAX_VALUE; }
    public long getUpgradeCost(int level) { return Math.round(4000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.GOLD_INGOT; }
}

class KeyGreedEnchantment implements CustomEnchantment {
    public String getName() { return "key_greed"; }
    public String getDisplayName() { return "Key Greed"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Chance d'obtenir des clés de coffres"; }
    public int getMaxLevel() { return 10; }
    public long getUpgradeCost(int level) { return Math.round(500000 * Math.pow(level, 1.8)); }
    public Material getDisplayMaterial() { return Material.TRIPWIRE_HOOK; }
}

class AbondanceEnchantment implements CustomEnchantment {
    public String getName() { return "abundance"; }
    public String getDisplayName() { return "Abondance"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Chance de doubler tous les gains"; }
    public int getMaxLevel() { return 100000; }
    public long getUpgradeCost(int level) { return Math.round(3000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.NETHER_STAR; }
}

class CombustionEnchantment implements CustomEnchantment {
    public String getName() { return "combustion"; }
    public String getDisplayName() { return "Combustion"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Plus tu mines, plus tes gains augmentent"; }
    public int getMaxLevel() { return 1000; }
    public long getUpgradeCost(int level) { return Math.round(5000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.FIRE_CHARGE; }
}

class PetXpEnchantment implements CustomEnchantment {
    public String getName() { return "pet_xp"; }
    public String getDisplayName() { return "Pet XP"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.ECONOMIC; }
    public String getDescription() { return "Augmente l'expérience des pets"; }
    public int getMaxLevel() { return Integer.MAX_VALUE; }
    public long getUpgradeCost(int level) { return Math.round(2000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.BONE; }
}

// Enchantements d'efficacité
class EfficiencyEnchantment implements CustomEnchantment {
    public String getName() { return "efficiency"; }
    public String getDisplayName() { return "Efficacité"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.EFFICIENCY; }
    public String getDescription() { return "Réduit le temps de minage"; }
    public int getMaxLevel() { return 50; }
    public long getUpgradeCost(int level) { return 10000L * level * level; }
    public Material getDisplayMaterial() { return Material.REDSTONE; }
}

class FortuneEnchantment implements CustomEnchantment {
    public String getName() { return "fortune"; }
    public String getDisplayName() { return "Fortune"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.EFFICIENCY; }
    public String getDescription() { return "Multiplie tous les drops"; }
    public int getMaxLevel() { return Integer.MAX_VALUE; }
    public long getUpgradeCost(int level) { return Math.round(2000 * Math.pow(level, 1.6)); }
    public Material getDisplayMaterial() { return Material.EMERALD; }
}

class DurabilityEnchantment implements CustomEnchantment {
    public String getName() { return "durability"; }
    public String getDisplayName() { return "Solidité"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.EFFICIENCY; }
    public String getDescription() { return "Augmente la durabilité"; }
    public int getMaxLevel() { return 20; }
    public long getUpgradeCost(int level) { return Math.round(10000 * Math.pow(level, 5)); }
    public Material getDisplayMaterial() { return Material.DIAMOND; }
}

// Enchantements de mobilité (avec Escalator déplacé ici)
class NightVisionEnchantment implements CustomEnchantment {
    public String getName() { return "night_vision"; }
    public String getDisplayName() { return "Vision Nocturne"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.MOBILITY; }
    public String getDescription() { return "Vision nocturne permanente"; }
    public int getMaxLevel() { return 1; }
    public long getUpgradeCost(int level) { return 150000; }
    public Material getDisplayMaterial() { return Material.GOLDEN_CARROT; }
}

class SpeedEnchantment implements CustomEnchantment {
    public String getName() { return "speed"; }
    public String getDisplayName() { return "Vitesse"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.MOBILITY; }
    public String getDescription() { return "Augmente la vitesse de déplacement"; }
    public int getMaxLevel() { return 3; }
    public long getUpgradeCost(int level) { return 100000L * level * level; }
    public Material getDisplayMaterial() { return Material.SUGAR; }
}

class HasteEnchantment implements CustomEnchantment {
    public String getName() { return "haste"; }
    public String getDisplayName() { return "Rapidité"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.MOBILITY; }
    public String getDescription() { return "Effet Haste permanent"; }
    public int getMaxLevel() { return 2; }
    public long getUpgradeCost(int level) { return Math.round(500000 * Math.pow(level, 3)); }
    public Material getDisplayMaterial() { return Material.BEACON; }
}

class JumpBoostEnchantment implements CustomEnchantment {
    public String getName() { return "jump_boost"; }
    public String getDisplayName() { return "Saut"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.MOBILITY; }
    public String getDescription() { return "Augmente la hauteur de saut"; }
    public int getMaxLevel() { return 5; }
    public long getUpgradeCost(int level) { return Math.round(75000 * Math.pow(level, 3)); }
    public Material getDisplayMaterial() { return Material.RABBIT_FOOT; }
}

// DÉPLACÉ: Escalator maintenant dans MOBILITY
class EscalatorEnchantment implements CustomEnchantment {
    public String getName() { return "escalator"; }
    public String getDisplayName() { return "Escalateur"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.MOBILITY; }
    public String getDescription() { return "Téléportation vers la surface"; }
    public int getMaxLevel() { return 1; }
    public long getUpgradeCost(int level) { return 200000; }
    public Material getDisplayMaterial() { return Material.ENDER_PEARL; }
}

// Enchantements spéciaux (Escalator retiré)
class LuckEnchantment implements CustomEnchantment {
    public String getName() { return "luck"; }
    public String getDisplayName() { return "Chance"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.SPECIAL; }
    public String getDescription() { return "Augmente les chances des Greeds"; }
    public int getMaxLevel() { return 500; }
    public long getUpgradeCost(int level) { return Math.round(3000 * Math.pow(level, 1.5)); }
    public Material getDisplayMaterial() { return Material.RABBIT_FOOT; }
}

class LaserEnchantment implements CustomEnchantment {
    public String getName() { return "laser"; }
    public String getDisplayName() { return "Laser"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.SPECIAL; }
    public String getDescription() { return "Chance de miner en ligne"; }
    public int getMaxLevel() { return 2000; }
    public long getUpgradeCost(int level) { return 20000L * level * level; }
    public Material getDisplayMaterial() { return Material.END_ROD; }
}

class ExplosionEnchantment implements CustomEnchantment {
    public String getName() { return "explosion"; }
    public String getDisplayName() { return "Explosion"; }
    public EnchantmentCategory getCategory() { return EnchantmentCategory.SPECIAL; }
    public String getDescription() { return "Chance de créer une explosion"; }
    public int getMaxLevel() { return 100; }
    public long getUpgradeCost(int level) { return Math.round(25000 * Math.pow(level, 1.05)); }
    public Material getDisplayMaterial() { return Material.TNT; }
}