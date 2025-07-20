package fr.prisontycoon.enchantments;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.BlockValueData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.PickaxeManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire des 18 enchantements custom
 * CORRIGÉ : Explosion/laser cassent visuellement, Fortune implémenté, Durability vérifié
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

        // CATÉGORIE UTILITÉS
        registerEnchantment(new EfficiencyEnchantment());
        registerEnchantment(new FortuneEnchantment());
        registerEnchantment(new DurabilityEnchantment());

        // CATÉGORIE MOBILITÉ
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
     * NOUVEAU : Traite un bloc MINÉ directement par le joueur (dans une mine) avec Fortune sur blocs
     */
    public void processBlockMined(Player player, Location blockLocation, Material blockType, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute aux statistiques de minage (MINÉ)
        playerData.addMinedBlock(blockType);

        int blocksToGive = calculateFortuneBlocks(player, playerData, blockType);

        // Ajoute les blocs à l'inventaire (quantité augmentée par Fortune)
        addBlocksToInventory(player, blockType, blocksToGive, blockLocation);

        boolean pickaxeBroken = PickaxeManager.isPickaxeBroken(player);
        if (!pickaxeBroken) {
            processGreedEnchantments(player, playerData, blockType, true);
            processSpecialEnchantments(player, playerData, blockLocation, mineName);
            updateCombustion(player, playerData);
            plugin.getEnchantmentBookManager().processMiningEnchantments(player, blockLocation);
            String activeProfession = playerData.getActiveProfession();
            if (activeProfession.equals("mineur")) {
                Random rand = new Random();
                if (rand.nextInt(100) == 0) {
                    plugin.getProfessionManager().addProfessionXP(player, "mineur", 1);
                }
            }
        }

        processTokenGreedWithPenalty(player, blockType, playerData);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * NOUVEAU & MODIFIÉ : Calcule le nombre de blocs à donner avec Fortune (formule rééquilibrée)
     */
    public int calculateFortuneBlocks(Player player, PlayerData playerData, Material blockType) {
        if (isPlayerPickaxeBroken(player)) {
            return 1; // Retourne 0 bloc bonus
        }

        int fortuneLevel = playerData.getEnchantmentLevel("fortune");

        // Pour chaque 100 niveaux, on gagne 1 bloc bonus garanti.
        int guaranteedBonus = fortuneLevel / 100;

        // Le reste (ex: niveau 250 -> 50) devient une probabilité d'obtenir un bloc supplémentaire.
        double chanceForExtra = (fortuneLevel % 100) / 100.0;

        int extraBlocks = 0;
        if (ThreadLocalRandom.current().nextDouble() < chanceForExtra) {
            extraBlocks = 1;
        }

        // Le total retourné correspond uniquement aux blocs BONUS.
        int baseBonusBlocks = guaranteedBonus + extraBlocks;

        // MODIFIÉ: Applique le bonus du cristal MineralGreed
        int finalBonusBlocks = plugin.getGlobalBonusManager().applyMineralGreedBonus(player, baseBonusBlocks);

        plugin.getPluginLogger().debug("Fortune " + fortuneLevel + " pour " + blockType.name() +
                ": " + finalBonusBlocks + " blocs bonus (" + baseBonusBlocks + " base + cristal)");

        return 1 + finalBonusBlocks;
    }

    /**
     * MODIFIÉ : Ajoute plusieurs blocs à l'inventaire avec priorité aux conteneurs
     */
    private void addBlocksToInventory(Player player, Material material, int quantity, Location blockLocation) {
        if (quantity <= 0) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int actuallyAdded = 0;
        int remaining = quantity;

        if (plugin.getEnchantmentBookManager().isEnchantmentActive(player, "autosell")) {
            plugin.getEnchantmentBookManager().processAutoSell(player, material, quantity);
            String activeProfession = playerData.getActiveProfession();
            if (activeProfession.equals("commercant")) {
                Random rand = new Random();
                if (rand.nextInt(1000) == 0) {
                    plugin.getProfessionManager().addProfessionXP(player, "commercant", 1);
                }
            }
            return;
        }

        // Gestion spéciale pour PlusValue
        String mineName = plugin.getConfigManager().getPlayerMine(blockLocation);
        if (plugin.getEnchantmentBookManager().isEnchantmentActive(player, "plusvalue") && !isPlayerPickaxeBroken(player)) {
            material = plugin.getEnchantmentBookManager().getHighestValueBlockInMine(mineName);
        }

        plugin.getPluginLogger().debug("Tentative d'ajout de " + quantity + "x " + material.name() + " pour " + player.getName());

        // NOUVEAU : Tente d'abord d'ajouter aux conteneurs
        while (remaining > 0) {
            ItemStack itemToAdd = new ItemStack(material, 1);
            boolean addedToContainer = plugin.getContainerManager().addItemToContainers(player, itemToAdd);
            if (addedToContainer) {
                actuallyAdded++;
                remaining--;
                plugin.getPluginLogger().debug("1x " + material.name() + " ajouté au conteneur");
            } else {
                // Aucun conteneur disponible, sort de la boucle
                break;
            }
        }

        // Si il reste des items, tente de les ajouter à l'inventaire normal
        if (remaining > 0) {
            ItemStack remainingStack = new ItemStack(material, remaining);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(remainingStack);

            if (leftover.isEmpty()) {
                // Tous les items restants ont été ajoutés à l'inventaire
                actuallyAdded += remaining;
                plugin.getPluginLogger().debug(remaining + "x " + material.name() + " ajoutés à l'inventaire normal");
            } else {
                // Calcule combien ont vraiment été ajoutés à l'inventaire
                int addedToInventory = remaining;
                for (ItemStack overflow : leftover.values()) {
                    addedToInventory -= overflow.getAmount();
                }

                actuallyAdded += addedToInventory;

                // Message d'avertissement moins fréquent pour l'inventaire plein
                if (!player.hasMetadata("inventory_full_warning") ||
                        System.currentTimeMillis() - player.getMetadata("inventory_full_warning").get(0).asLong() > 30000) {

                    int droppedCount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    player.sendMessage("§c⚠️ Inventaire et conteneurs pleins! " + droppedCount + " items droppés au sol.");
                    player.setMetadata("inventory_full_warning", new FixedMetadataValue(plugin, System.currentTimeMillis()));

                    // Suggestion d'utiliser /sell all
                    player.sendMessage("§e💡 Utilisez §a/sell all §epour vider vos conteneurs et inventaire!");
                }
            }
        }

        // NOUVEAU : Track les blocs ajoutés à l'inventaire/conteneurs
        if (actuallyAdded > 0) {
            playerData.addBlocksToInventory(actuallyAdded);
        }

        plugin.getPluginLogger().debug("Blocs ajoutés au total: " + actuallyAdded + "/" + quantity + "x " + material.name() +
                " (conteneurs + inventaire + droppés)");
    }

    /**
     * MODIFIÉ : Traite un bloc CASSÉ par laser/explosion et applique Fortune
     */
    public void processBlockDestroyed(Player player, Location blockLocation, Material blockType, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute aux statistiques de destruction (CASSÉ)
        playerData.addDestroyedBlocks(1);

        // NOUVEAU: Applique Fortune sur les blocs cassés
        int blocksfortune = calculateFortuneBlocks(player, playerData, blockType);
        int blocksToGive = blocksfortune + 1;
        addBlocksToInventory(player, blockType, blocksToGive, blockLocation);

        // LIMITATION : Seuls Money/Token/Exp Greed s'appliquent sur les gains de base
        processGreedEnchantments(player, playerData, blockType, false);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * NOUVEAU : Traite un bloc miné HORS mine (restrictions)
     */
    public void processBlockMinedOutsideMine(Player player, Material blockType) {
        plugin.getPluginLogger().debug("Bloc miné hors mine: " + blockType + " par " + player.getName() +
                " (seuls efficacité/solidité/mobilité actifs)");
    }

    /**
     * MODIFIÉ : Traite les enchantements Greed avec le nouveau GlobalBonusManager
     */
    private void processGreedEnchantments(Player player, PlayerData playerData, Material blockType, boolean isMinedBlock) {

        int luckLevel = playerData.getEnchantmentLevel("luck");

        // MODIFIÉ: Utilise le GlobalBonusManager pour l'efficacité de combustion
        double baseCombustionMultiplier = playerData.getCombustionMultiplier();
        double combustionMultiplier = plugin.getGlobalBonusManager().getCombustionEfficiency(player, baseCombustionMultiplier);

        double abundanceMultiplier = playerData.isAbundanceActive() ? 2.0 : 1.0;
        BlockValueData blockValue = plugin.getConfigManager().getBlockValue(blockType);

        // Token Greed - Toujours actif (blocs minés ET cassés)
        int tokenGreedLevel = playerData.getEnchantmentLevel("token_greed");
        if (tokenGreedLevel > 0) {
            double baseChance = plugin.getConfigManager().getEnchantmentSetting("greed.base-chance", 0.05);
            double luckBonus = luckLevel * plugin.getConfigManager().getEnchantmentSetting("greed.luck-bonus-per-level", 0.002);
            double totalChance = baseChance + luckBonus;

            if (ThreadLocalRandom.current().nextDouble() < totalChance) {
                long blockTokens = blockValue.getTokens();
                long baseGains = Math.round((tokenGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.token-multiplier", 5) + blockTokens) * combustionMultiplier * abundanceMultiplier);

                // MODIFIÉ: Utilise le GlobalBonusManager au lieu de CristalBonusHelper
                long finalGains = plugin.getGlobalBonusManager().applyTokenGreedBonus(player, baseGains);

                playerData.addTokensViaPickaxe(finalGains);
                playerData.addGreedTrigger();

            }
        }

        // Exp Greed - Seulement sur blocs MINÉS
        int expGreedLevel = playerData.getEnchantmentLevel("exp_greed");
        if (expGreedLevel > 0) {
            double baseChance = plugin.getConfigManager().getEnchantmentSetting("greed.base-chance", 0.05);
            double luckBonus = luckLevel * plugin.getConfigManager().getEnchantmentSetting("greed.luck-bonus-per-level", 0.002);
            double totalChance = baseChance + luckBonus;

            if (ThreadLocalRandom.current().nextDouble() < totalChance) {
                long blockExp = blockValue.getExperience();
                long baseGains = Math.round((expGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.exp-multiplier", 50) + blockExp * 3) * combustionMultiplier * abundanceMultiplier);

                // MODIFIÉ: Utilise le GlobalBonusManager au lieu de CristalBonusHelper
                long finalGains = plugin.getGlobalBonusManager().applyExpGreedBonus(player, baseGains);

                playerData.addExperienceViaPickaxe(finalGains);
                playerData.addGreedTrigger();

                // Met à jour l'expérience vanilla
                plugin.getEconomyManager().updateVanillaExpFromCustom(player, playerData.getExperience());
            }
        }

        // Money Greed - Toujours actif (blocs minés ET cassés)
        int moneyGreedLevel = playerData.getEnchantmentLevel("money_greed");
        if (moneyGreedLevel > 0) {
            double baseChance = plugin.getConfigManager().getEnchantmentSetting("greed.base-chance", 0.05);
            double luckBonus = luckLevel * plugin.getConfigManager().getEnchantmentSetting("greed.luck-bonus-per-level", 0.002);
            double totalChance = baseChance + luckBonus;

            if (ThreadLocalRandom.current().nextDouble() < totalChance) {
                long blockCoins = blockValue.getCoins();
                long baseGains = Math.round((moneyGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.money-multiplier", 10) + blockCoins * 2) * combustionMultiplier * abundanceMultiplier);

                // MODIFIÉ: Utilise le GlobalBonusManager au lieu de CristalBonusHelper
                long finalGains = plugin.getGlobalBonusManager().applyMoneyGreedBonus(player, baseGains);

                playerData.addCoinsViaPickaxe(finalGains);
                playerData.addGreedTrigger();
            }
        }


        if (isMinedBlock) {
            // Key Greed - chance fixe (pas affectée par Luck selon demande)
            int keyGreedLevel = playerData.getEnchantmentLevel("key_greed");
            if (keyGreedLevel > 0) {
                double chance = plugin.getConfigManager().getEnchantmentSetting("keys.base-chance", 0.0001) * keyGreedLevel;
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    giveRandomKey(player);
                    playerData.addKeyObtained();
                }
            }

            // CORRIGÉ : Abundance avec vérification du cooldown
            int abundanceLevel = playerData.getEnchantmentLevel("abundance");
            if (abundanceLevel > 0 && !playerData.isAbundanceActive() && !playerData.isAbundanceOnCooldown()) {
                double chance = plugin.getConfigManager().getEnchantmentSetting("abundance.base-chance", 0.000001) * abundanceLevel;
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    // MODIFIÉ: Calcule la durée avec le bonus du cristal
                    int duration = plugin.getCristalBonusHelper().getAbondanceDuration(player, 60); // 60s de base

                    playerData.activateAbundance(duration * 1000L);
                    player.sendMessage("§6🌟 ABONDANCE ACTIVÉE! §eGains doublés pendant " + duration + " secondes!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

                    plugin.getPluginLogger().info("Abondance activée pour " + player.getName() +
                            " (niveau " + abundanceLevel + ", durée " + duration + "s)");
                }
            }
        }
    }

    /**
     * MODIFIÉ : Applique les enchantements spéciaux SEULEMENT sur les blocs MINÉS
     */
    private void processSpecialEnchantments(Player player, PlayerData playerData,
                                            Location blockLocation, String mineName) {

        int laserLevel = playerData.getEnchantmentLevel("laser");
        int explosionLevel = playerData.getEnchantmentLevel("explosion");

        // Laser
        if (laserLevel > 0) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("special.laser.base-chance", 0.00002) * laserLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateLaser(player, blockLocation, mineName, false);
            }
        }

        // Explosion
        if (explosionLevel > 0) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("special.explosion.base-chance", 0.0005) * explosionLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateExplosion(player, blockLocation, mineName);
            }
        }
    }

    /**
     * NOUVEAU : Traite Token Greed avec malus de 90% quand la pioche est cassée
     */
    private void processTokenGreedWithPenalty(Player player, Material blockType, PlayerData playerData) {
        int tokenGreedLevel = playerData.getEnchantmentLevel("token_greed");
        if (tokenGreedLevel <= 0) return;

        double chance = plugin.getConfigManager().getEnchantmentSetting("greed.base-chance", 0.05);
        double penaltyMultiplier = PickaxeManager.getPickaxePenaltyMultiplier(player);

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            BlockValueData blockValue = plugin.getConfigManager().getBlockValue(blockType);
            long blockTokens = blockValue.getTokens();
            long baseGains = Math.round((tokenGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.token-multiplier", 5) + blockTokens) * penaltyMultiplier);

            // MODIFIÉ: Utilise le GlobalBonusManager même avec la malus
            long finalGains = plugin.getGlobalBonusManager().applyTokenGreedBonus(player, baseGains);

            plugin.getEconomyManager().addTokens(player, finalGains);
            playerData.addGreedTrigger();
        }
    }

    /**
     * MODIFIÉ : Met à jour la combustion SEULEMENT si la pioche n'est pas cassée
     */
    private void updateCombustion(Player player, PlayerData playerData) {
        if (isPlayerPickaxeBroken(player)) {
            return;
        }

        int combustionLevel = playerData.getEnchantmentLevel("combustion");
        if (combustionLevel > 0) {
            int gainPerBlock = Math.max(1, combustionLevel / 10);
            playerData.updateCombustion(gainPerBlock);
        }
    }

    /**
     * MODIFIÉ : Donne une clé aléatoire au joueur - Utilise les conteneurs en priorité
     */
    private void giveRandomKey(Player player) {
        double rand = ThreadLocalRandom.current().nextDouble();
        String keyType;
        String keyColor;

        // CORRIGÉ : Création manuelle du Map au lieu d'utiliser getEnchantmentSetting avec Map
        Map<String, Double> keyProbabilities = new HashMap<>();
        keyProbabilities.put("cristal", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.cristal", 0.00005));
        keyProbabilities.put("legendaire", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.legendaire", 0.00995));
        keyProbabilities.put("rare", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.rare", 0.09));
        keyProbabilities.put("peu-commune", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.peu-commune", 0.20));
        keyProbabilities.put("commune", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.commune", 0.70));

        if (rand < keyProbabilities.get("cristal")) {
            keyType = "Cristal";
            keyColor = "§d";
        } else if (rand < keyProbabilities.get("legendaire")) {
            keyType = "Légendaire";
            keyColor = "§6";
        } else if (rand < keyProbabilities.get("rare")) {
            keyType = "Rare";
            keyColor = "§5";
        } else if (rand < keyProbabilities.get("peu-commune")) {
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
                "§7Utilise cette clé pour ouvrir des coffres!"
        ));
        key.setItemMeta(meta);

        // NOUVEAU : Tente d'abord d'ajouter aux conteneurs
        boolean addedToContainer = plugin.getContainerManager().addItemToContainers(player, key);

        if (addedToContainer) {
            player.sendMessage("§e🗝️ Clé " + keyColor + keyType + " §eajoutée à vos conteneurs!");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.5f);
            plugin.getPluginLogger().debug("Clé " + keyType + " ajoutée au conteneur de " + player.getName());
        } else {
            // Pas de conteneur disponible, essaie l'inventaire normal
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(key);
                player.sendMessage("§e🗝️ Clé " + keyColor + keyType + " §eobtenue!");
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.5f);
            } else {
                // Inventaire aussi plein, drop au sol
                player.getWorld().dropItemNaturally(player.getLocation(), key);
                player.sendMessage("§e🗝️ Clé " + keyColor + keyType + " §edroppée au sol (inventaire et conteneurs pleins)!");
            }
        }
    }

    /**
     * NOUVEAU : Désactive de force l'abondance et reset la combustion quand la pioche est cassée
     */
    public void forceDisableAbundanceAndResetCombustion(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        boolean changed = false;

        // Désactive l'abondance si elle est active
        if (playerData.isAbundanceActive()) {
            playerData.deactivateAbundance(); // Suppose que cette méthode existe dans PlayerData
            changed = true;

            plugin.getPluginLogger().info("Abondance forcément désactivée pour " + player.getName() + " (pioche cassée)");
        }

        // Reset la combustion si elle est active
        if (playerData.getCombustionLevel() > 0) {
            playerData.setCombustionLevel(0);
            changed = true;

            plugin.getPluginLogger().info("Combustion remise à zéro pour " + player.getName() + " (pioche cassée)");
        }

        // Marque les données comme modifiées si des changements ont été effectués
        if (changed) {
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        }
    }

    /**
     * NOUVEAU : Vérifie si la pioche du joueur est cassée (méthode helper)
     */
    public boolean isPlayerPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * CORRIGÉ : Active l'effet laser. Le message final est envoyé après que tous les échos
     * se soient déclenchés, et il inclut le total des blocs détruits par le laser initial et les échos.
     * La fonction retourne le nombre de blocs détruits par CET appel spécifique de laser.
     */
    private int activateLaser(Player player, Location start, String mineName, boolean isEcho) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return 0;

        var direction = player.getLocation().getDirection().normalize();
        int blocksDestroyed = 0;
        int maxDistance = plugin.getConfigManager().getEnchantmentSetting("special.laser.max-distance", 1000);

        // CORRIGÉ : Continue jusqu'au bout de la mine, ne s'arrête pas aux blocs d'air
        for (int distance = 1; distance <= maxDistance; distance++) {
            Location target = start.clone().add(direction.clone().multiply(distance));

            // Arrête si on sort de la mine
            if (!mineData.contains(target)) {
                break;
            }

            Material originalType = target.getBlock().getType();

            // NOUVEAU : Protection beacon - arrête le laser
            if (originalType == Material.BEACON) {
                plugin.getPluginLogger().debug("Laser arrêté par un beacon à " + target);
                break;
            }

            if (originalType != Material.AIR) {
                // CORRECTION : CASSE visuellement (met en AIR)
                target.getBlock().setType(Material.AIR);
                blocksDestroyed++;

                // Animation de particules
                target.getWorld().spawnParticle(Particle.BLOCK, target.clone().add(0.5, 0.5, 0.5),
                        10, 0.3, 0.3, 0.3, 0.1, originalType.createBlockData());

                // IMPORTANT : Bloc CASSÉ (pas miné) - pas de récursion enchants spéciaux
                processBlockDestroyed(player, target, originalType, mineName);
            }

            // Animation du rayon laser sur TOUS les blocs (air et solides)
            target.getWorld().spawnParticle(Particle.DUST, target.clone().add(0.5, 0.5, 0.5), 2,
                    0.1, 0.1, 0.1, 0, new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
        }

        // Animation du rayon laser complet
        for (int i = 0; i < maxDistance && i < 100; i++) {
            Location particleLocation = start.clone().add(direction.clone().multiply(i));

            // Arrête l'animation si on sort de la mine
            if (!mineData.contains(particleLocation)) {
                break;
            }

            particleLocation.getWorld().spawnParticle(Particle.DUST, particleLocation, 1,
                    0.05, 0.05, 0.05, 0, new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
        }

        // Si c'est l'appel initial (pas un écho), on gère les échos et le message final.
        if (!isEcho) {
            int totalBlocks = blocksDestroyed;
            int echoCount = 0;

            if (plugin.getCristalBonusHelper().shouldTriggerEcho(player)) {
                int potentialEchos = plugin.getCristalBonusHelper().getEchoCount(player);
                if (potentialEchos > 0) {
                    echoCount = potentialEchos;
                    // Déclenche les échos et ajoute les blocs détruits au total
                    totalBlocks += triggerEchos(player, start, echoCount, mineName, "laser");
                }
            }

            // Envoie le message final basé sur la présence d'échos
            if (echoCount > 0) {
                player.sendMessage("§c⚡ Laser (+" + echoCount + " échos) activé! §e" + totalBlocks + " blocs détruits au total !");
            } else {
                player.sendMessage("§c⚡ Laser activé! §e" + blocksDestroyed + " blocs détruits en ligne !");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2.0f);
        }

        return blocksDestroyed;
    }

    /**
     * MODIFIÉ : Active l'effet explosion avec protection beacon
     */
    private void activateExplosion(Player player, Location center, String mineName) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return;

        // Rayon aléatoire selon config
        int minRadius = plugin.getConfigManager().getEnchantmentSetting("special.explosion.min-radius", 1);
        int maxRadius = plugin.getConfigManager().getEnchantmentSetting("special.explosion.max-radius", 3);
        int radius = ThreadLocalRandom.current().nextInt(minRadius, maxRadius + 1);
        int blocksDestroyed = 0;

        // Animation d'explosion
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.5, 0.5, 0.1);

        // Explosion sphérique centrée sur le bloc miné
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Vérifie si le bloc est dans le rayon sphérique
                    if (x * x + y * y + z * z <= radius * radius) {
                        Location target = center.clone().add(x, y, z);

                        // Vérifie si dans la mine
                        if (mineData.contains(target)) {
                            Material originalType = target.getBlock().getType();

                            // NOUVEAU : Protection beacon - ne peut pas être cassé
                            if (originalType == Material.BEACON) {
                                plugin.getPluginLogger().debug("Explosion bloquée par beacon à " + target);
                                continue; // Ignore ce bloc
                            }

                            // Ne casse que les blocs solides (pas l'air)
                            if (originalType != Material.AIR) {
                                // CORRECTION : CASSE visuellement (met en AIR)
                                target.getBlock().setType(Material.AIR);
                                blocksDestroyed++;

                                // Animation de particules d'explosion
                                target.getWorld().spawnParticle(Particle.BLOCK, target.clone().add(0.5, 0.5, 0.5),
                                        8, 0.4, 0.4, 0.4, 0.1, originalType.createBlockData());

                                // IMPORTANT : Bloc CASSÉ (pas miné) - pas de récursion enchants spéciaux
                                processBlockDestroyed(player, target, originalType, mineName);
                            }
                        }
                    }
                }
            }
        }

        player.sendMessage("§4💥 Explosion rayon " + radius + "! §e" + blocksDestroyed + " blocs détruits!");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    /**
     * NOUVEAU & CORRIGÉ : Déclenche les échos pour Laser/Explosion et retourne le nombre total
     * de blocs détruits par ces échos.
     */
    private int triggerEchos(Player player, Location origin, int echoCount, String mineName, String enchantmentType) {
        int totalEchoBlocks = 0;
        for (int i = 0; i < echoCount; i++) {
            // Calculer direction aléatoire
            Vector randomDirection = new Vector(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2
            ).normalize();

            Location echoLocation = origin.clone().add(randomDirection.multiply(3 + i));

            // Déclencher à nouveau l'enchantement à cette position (en mode écho)
            if ("laser".equalsIgnoreCase(enchantmentType)) {
                // Ajoute le nombre de blocs détruits par l'écho au total
                totalEchoBlocks += activateLaser(player, echoLocation, mineName, true);
            }

            // Effets visuels pour distinguer les échos
            player.getWorld().spawnParticle(Particle.DUST, echoLocation, 20, 0.5, 0.5, 0.5);
        }
        return totalEchoBlocks;
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
     * CORRIGÉ : Améliore un enchantement avec calcul de coûts identique au GUI
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

        // CORRECTION : Utilise la même logique que le GUI pour calculer les niveaux réellement achetables
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
     * NOUVEAU : Vérifie si un joueur peut utiliser l'auto-upgrade
     */
    public boolean canUseAutoUpgrade(Player player) {
        return player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin");
    }
}


// Implémentations des enchantements avec coûts depuis config
class TokenGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "token_greed";
    }

    public String getDisplayName() {
        return "Token Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir des tokens bonus";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(5000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.GOLD_NUGGET;
    }
}

class ExpGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "exp_greed";
    }

    public String getDisplayName() {
        return "Exp Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir de l'expérience bonus";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(3000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.EXPERIENCE_BOTTLE;
    }
}

class MoneyGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "money_greed";
    }

    public String getDisplayName() {
        return "Money Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir des coins bonus";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(4000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.GOLD_INGOT;
    }
}

class KeyGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "key_greed";
    }

    public String getDisplayName() {
        return "Key Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir des clés de coffres";
    }

    public int getMaxLevel() {
        return 10;
    }

    public long getUpgradeCost(int level) {
        return Math.round(500000 * Math.pow(level, 1.8));
    }

    public Material getDisplayMaterial() {
        return Material.TRIPWIRE_HOOK;
    }
}

class AbondanceEnchantment implements CustomEnchantment {
    public String getName() {
        return "abundance";
    }

    public String getDisplayName() {
        return "Abondance";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance de doubler tous les gains";
    }

    public int getMaxLevel() {
        return 100000;
    }

    public long getUpgradeCost(int level) {
        return Math.round(3000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.NETHER_STAR;
    }
}

class CombustionEnchantment implements CustomEnchantment {
    public String getName() {
        return "combustion";
    }

    public String getDisplayName() {
        return "Combustion";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Plus tu mines, plus tes gains augmentent";
    }

    public int getMaxLevel() {
        return 1000;
    }

    public long getUpgradeCost(int level) {
        return Math.round(5000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.FIRE_CHARGE;
    }
}

class PetXpEnchantment implements CustomEnchantment {
    public String getName() {
        return "pet_xp";
    }

    public String getDisplayName() {
        return "Pet XP";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Augmente l'expérience des pets";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(2000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.BONE;
    }
}

// Enchantements d'efficacité
class EfficiencyEnchantment implements CustomEnchantment {
    public String getName() {
        return "efficiency";
    }

    public String getDisplayName() {
        return "Efficacité";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Réduit le temps de minage";
    }

    public int getMaxLevel() {
        return 200;
    }

    public long getUpgradeCost(int level) {
        return 10000L * level * level;
    }

    public Material getDisplayMaterial() {
        return Material.REDSTONE;
    }
}

class FortuneEnchantment implements CustomEnchantment {
    public String getName() {
        return "fortune";
    }

    public String getDisplayName() {
        return "Fortune";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Multiplie tous les drops";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(2000 * Math.pow(level, 1.6));
    }

    public Material getDisplayMaterial() {
        return Material.EMERALD;
    }
}

class DurabilityEnchantment implements CustomEnchantment {
    public String getName() {
        return "durability";
    }

    public String getDisplayName() {
        return "Solidité";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Augmente la durabilité";
    }

    public int getMaxLevel() {
        return 20;
    }

    public long getUpgradeCost(int level) {
        return Math.round(10000 * Math.pow(level, 5));
    }

    public Material getDisplayMaterial() {
        return Material.DIAMOND;
    }
}

// Enchantements de mobilité
class NightVisionEnchantment implements CustomEnchantment {
    public String getName() {
        return "night_vision";
    }

    public String getDisplayName() {
        return "Vision Nocturne";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Vision nocturne permanente";
    }

    public int getMaxLevel() {
        return 1;
    }

    public long getUpgradeCost(int level) {
        return 150000;
    }

    public Material getDisplayMaterial() {
        return Material.GOLDEN_CARROT;
    }
}

class SpeedEnchantment implements CustomEnchantment {
    public String getName() {
        return "speed";
    }

    public String getDisplayName() {
        return "Vitesse";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Augmente la vitesse de déplacement";
    }

    public int getMaxLevel() {
        return 3;
    }

    public long getUpgradeCost(int level) {
        return 100000L * level * level;
    }

    public Material getDisplayMaterial() {
        return Material.SUGAR;
    }
}

class HasteEnchantment implements CustomEnchantment {
    public String getName() {
        return "haste";
    }

    public String getDisplayName() {
        return "Célérité";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Effet Célérité permanent";
    }

    public int getMaxLevel() {
        return 10;
    }

    public long getUpgradeCost(int level) {
        return Math.round(500000 * Math.pow(level, 3));
    }

    public Material getDisplayMaterial() {
        return Material.BEACON;
    }
}

class JumpBoostEnchantment implements CustomEnchantment {
    public String getName() {
        return "jump_boost";
    }

    public String getDisplayName() {
        return "Saut";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Augmente la hauteur de saut";
    }

    public int getMaxLevel() {
        return 5;
    }

    public long getUpgradeCost(int level) {
        return Math.round(75000 * Math.pow(level, 3));
    }

    public Material getDisplayMaterial() {
        return Material.RABBIT_FOOT;
    }
}

class EscalatorEnchantment implements CustomEnchantment {
    public String getName() {
        return "escalator";
    }

    public String getDisplayName() {
        return "Escalateur";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Téléportation vers la surface";
    }

    public int getMaxLevel() {
        return 1;
    }

    public long getUpgradeCost(int level) {
        return 200000;
    }

    public Material getDisplayMaterial() {
        return Material.ENDER_PEARL;
    }
}

// Enchantements spéciaux
class LuckEnchantment implements CustomEnchantment {
    public String getName() {
        return "luck";
    }

    public String getDisplayName() {
        return "Chance";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.SPECIAL;
    }

    public String getDescription() {
        return "Augmente les chances des Greeds";
    }

    public int getMaxLevel() {
        return 500;
    }

    public long getUpgradeCost(int level) {
        return Math.round(3000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.RABBIT_FOOT;
    }
}

class LaserEnchantment implements CustomEnchantment {
    public String getName() {
        return "laser";
    }

    public String getDisplayName() {
        return "Laser";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.SPECIAL;
    }

    public String getDescription() {
        return "Chance de miner en ligne";
    }

    public int getMaxLevel() {
        return 2000;
    }

    public long getUpgradeCost(int level) {
        return 20000L * level * level;
    }

    public Material getDisplayMaterial() {
        return Material.END_ROD;
    }
}

class ExplosionEnchantment implements CustomEnchantment {
    public String getName() {
        return "explosion";
    }

    public String getDisplayName() {
        return "Explosion";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.SPECIAL;
    }

    public String getDescription() {
        return "Chance de créer une explosion";
    }

    public int getMaxLevel() {
        return 100;
    }

    public long getUpgradeCost(int level) {
        return Math.round(25000 * Math.pow(level, 1.05));
    }

    public Material getDisplayMaterial() {
        return Material.TNT;
    }
}